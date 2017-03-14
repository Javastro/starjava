package uk.ac.starlink.topcat.plot2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoundedRangeModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import uk.ac.starlink.topcat.ToggleButtonModel;
import uk.ac.starlink.topcat.TopcatUtils;
import uk.ac.starlink.ttools.plot.Range;
import uk.ac.starlink.ttools.plot.Style;
import uk.ac.starlink.ttools.plot2.AuxScale;
import uk.ac.starlink.ttools.plot2.Decoration;
import uk.ac.starlink.ttools.plot2.Drawing;
import uk.ac.starlink.ttools.plot2.Equality;
import uk.ac.starlink.ttools.plot2.Gang;
import uk.ac.starlink.ttools.plot2.Ganger;
import uk.ac.starlink.ttools.plot2.LayerOpt;
import uk.ac.starlink.ttools.plot2.PlotLayer;
import uk.ac.starlink.ttools.plot2.PlotPlacement;
import uk.ac.starlink.ttools.plot2.PlotUtil;
import uk.ac.starlink.ttools.plot2.ReportMap;
import uk.ac.starlink.ttools.plot2.ShadeAxis;
import uk.ac.starlink.ttools.plot2.ShadeAxisFactory;
import uk.ac.starlink.ttools.plot2.Slow;
import uk.ac.starlink.ttools.plot2.SubCloud;
import uk.ac.starlink.ttools.plot2.Subrange;
import uk.ac.starlink.ttools.plot2.Surface;
import uk.ac.starlink.ttools.plot2.SurfaceFactory;
import uk.ac.starlink.ttools.plot2.ZoneContent;
import uk.ac.starlink.ttools.plot2.config.ConfigMap;
import uk.ac.starlink.ttools.plot2.config.StyleKeys;
import uk.ac.starlink.ttools.plot2.data.DataSpec;
import uk.ac.starlink.ttools.plot2.data.DataStore;
import uk.ac.starlink.ttools.plot2.data.DataStoreFactory;
import uk.ac.starlink.ttools.plot2.data.StepDataStore;
import uk.ac.starlink.ttools.plot2.data.TupleSequence;
import uk.ac.starlink.ttools.plot2.paper.Compositor;
import uk.ac.starlink.ttools.plot2.paper.PaperType;
import uk.ac.starlink.ttools.plot2.paper.PaperTypeSelector;

/**
 * Component which paints plot graphics for Topcat.
 * This is the throbbing heart of the plot classes.
 *
 * <p>It is supplied at construction time with various objects capable of
 * acquiring (presumably from a GUI) information required to specify a plot,
 * and its {@link #replot replot} method conceptually acquires
 * all that information and prepares a plot accordingly.
 * The plot is cached to an icon (probably an image) which is in
 * turn painted by <code>paintComponent</code>.
 * <code>replot</code> should therefore be called any time the plot
 * information has changed, or may have changed.
 *
 * <p>In actual fact <code>replot</code> additionally
 * expends a lot of effort to work out whether it can avoid doing some or
 * all of the work required for the plot on each occasion,
 * by caching and attempting to re-use the restults of various
 * computational steps if they have not become outdated.
 * The capability to do this as efficiently as possible drives quite a bit
 * of the design of the rest of the plotting framework, in particular
 * the requirement that a number of the objects determining plot content
 * can be assessed for equality to tell whether they have changed
 * materially since last time.
 *
 * <p>This component manages all the storage and caching of expensive
 * (memory-intensive) resources: layer plans and data stores.
 * Such resources should not be cached or otherwise held on to by
 * long-lived reference elsewhere in the application.
 *
 * <p>This component also manages threading to get computation done
 * in appropriate threads (and not on the EDT).  At time of writing
 * there are probably some improvements that can be made in that respect.
 *
 * <p>This component is an ActionListener - receiving any action will
 * prompt a (potential) replot.
 *
 * @author   Mark Taylor
 * @since    12 Mar 2013
 */
public class PlotPanel<P,A> extends JComponent implements ActionListener {

    private final DataStoreFactory storeFact_;
    private final SurfaceFactory<P,A> surfFact_;
    private final Factory<Ganger<P,A>> gangerFact_;
    private final Factory<ZoneDef<P,A>[]> zonesFact_;
    private final Factory<PlotPosition> posFact_;
    private final PaperTypeSelector ptSel_;
    private final Compositor compositor_;
    private final ToggleButtonModel sketchModel_;
    private final BoundedRangeModel progModel_;
    private final ToggleButtonModel showProgressModel_;
    private final ToggleButtonModel axisLockModel_;
    private final ToggleButtonModel auxLockModel_;
    private final List<ChangeListener> changeListenerList_;
    private final ExecutorService plotExec_;
    private final ExecutorService noteExec_;
    private final Workings<A> dummyWorkings_;
    private final P[] profiles0_;
    private PlotJob<P,A> plotJob_;
    private PlotJobRunner plotRunner_;
    private Cancellable plotNoteRunner_;
    private Cancellable extraNoteRunner_;
    private Workings<A> workings_;
    private Surface[] latestSurfaces_;
    private Map<SubCloud,double[]> highlightMap_;
    private Decoration navDecoration_;

    private static final boolean WITH_SCROLL = true;
    private static final Icon HIGHLIGHTER = new HighlightIcon();
    private static final Logger logger_ =
        Logger.getLogger( "uk.ac.starlink.ttools.plot2" );

    /**
     * Constructor.  Factories to gather various information required
     * for the plot are passed in.
     * These are interrogated when a (possibly) new plot is triggered.
     *
     * <p>Information flow is, or should be, one way - this component
     * reads the data and the plot does not have side-effects on its
     * constituent components, since passing information both ways
     * generally leads to a lot of confusion.  In fact as currently
     * written one GUI compoent, the AxisController, is passed in and
     * can be affected.  It would be better to sanitize that.
     *
     * <p>A progress bar model is used so that progress can be logged
     * whenever a scan through the data of one or several tables is under way.
     * An alternative would be to pass a JProgressBar itself, so that a
     * new model could be inserted every time a new progress operation started.
     * That would actually be easier to use, but doing it this way makes it
     * more obvious if multiple progress operations are happening concurrently,
     * which as it stands they should not be.
     *
     * @param  storeFact   data store factory implementation
     * @param  surfFact   surface factory
     * @param  gangerFact  factory for defining how multi-zone plots are grouped
     * @param  zonesFact  acquires per-zone information
     * @param  posFact  supplier of plot position settings
     * @param  ptSel   rendering policy
     * @param  compositor  compositor for composition of transparent pixels
     * @param  sketchModel   model to decide whether intermediate sketch frames
     *                       are posted for slow plots
     * @param  progModel  progress bar model for showing plot progress
     * @param  showProgressModel  model to decide whether data scan operations
     *                            are reported to the progress bar model
     * @param  axisLockModel  model to determine whether axis auto-rescaling
     *                        should be inhibited
     * @param  auxLockModel  model to determine whether aux range auto-rescaling
     *                       should be inhibited
     */
    public PlotPanel( DataStoreFactory storeFact, SurfaceFactory<P,A> surfFact,
                      Factory<Ganger<P,A>> gangerFact,
                      Factory<ZoneDef<P,A>[]> zonesFact,
                      Factory<PlotPosition> posFact,
                      PaperTypeSelector ptSel, Compositor compositor,
                      ToggleButtonModel sketchModel,
                      BoundedRangeModel progModel,
                      ToggleButtonModel showProgressModel,
                      ToggleButtonModel axisLockModel,
                      ToggleButtonModel auxLockModel ) {
        storeFact_ = progModel == null
                   ? storeFact
                   : new ProgressDataStoreFactory( storeFact, progModel );
        surfFact_ = surfFact;
        gangerFact_ = gangerFact;
        zonesFact_ = zonesFact;
        posFact_ = posFact;
        ptSel_ = ptSel;
        compositor_ = compositor;
        sketchModel_ = sketchModel;
        progModel_ = progModel;
        showProgressModel_ = showProgressModel;
        axisLockModel_ = axisLockModel;
        auxLockModel_ = auxLockModel;
        changeListenerList_ = new ArrayList<ChangeListener>();
        plotExec_ = Executors.newSingleThreadExecutor();
        noteExec_ = Runtime.getRuntime().availableProcessors() > 1
                  ? Executors.newSingleThreadExecutor()
                  : plotExec_;
        profiles0_ = PlotUtil.createProfileArray( surfFact_, 0 );
        dummyWorkings_ = createDummyWorkings( surfFact );
        plotRunner_ = new PlotJobRunner();
        plotNoteRunner_ = new Cancellable();
        extraNoteRunner_ = new Cancellable();
        setPreferredSize( new Dimension( 500, 400 ) );
        addComponentListener( new ComponentAdapter() {
            @Override
            public void componentResized( ComponentEvent evt ) {
                replot();
            }
        } );
        highlightMap_ = new HashMap<SubCloud,double[]>();
        clearData();
    }

    /**
     * Invokes replot.
     */
    public void actionPerformed( ActionEvent evt ) {
        replot();
    }

    /**
     * Call this on the event dispatch thread to indicate that the plot
     * inputs may have changed, to trigger a new plot.
     * The plot will be regenerated and painted at a later stage if required.
     * This method is fairly cheap to call if the plot has not in fact
     * changed.
     */
    public void replot() {

        /* We create the plot job here and queue it for (slightly)
         * later execution on the (same) event dispatch thread.
         * The point of this is that it is common for a single user
         * intervention (sliding a slider, selecting from a combo box)
         * to trigger not one but several ActionEvents in quick succession
         * (which may or may not be a consequence of sloppy coding of the GUI).
         * These actions probably all represent the same end state, or
         * even if they don't it's not desirable to plot the earlier ones.
         * Doing it like this gives a chance to ignore the earlier ones
         * in a quick sequence, and only bother to do the plot for the
         * last one. */

        /* Find out if we are already waiting for calculation of a replot. */
        boolean isJobPending = plotJob_ != null;

        /* Gather plot inputs and prepare a replot specification. */
        plotJob_ = createPlotJob();

        /* If a job is pending, there must already be a runnable in the
         * event queue which is ready to run the next plot job to appear.
         * In that case, resetting the value of plotJob_ as we have just
         * done without queueing a new runnable will cause the new replot
         * to get done (discarding the previously queued one). *./

        /* However if no job is pending, queue one now.  */
        if ( ! isJobPending ) {
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {

                    /* Not expected to be be null, but might be if clearData()
                     * has been called (probably during window disposal). */
                    if ( plotJob_ != null ) {
                        executePlotJob( plotJob_ );
                        plotJob_ = null;
                    }
                }
            } );
        }
    }

    /**
     * Called from the event dispatch thread to schedule execution of a
     * plot job.
     *
     * @param  plotJob  plot job to run
     */
    private void executePlotJob( PlotJob<P,A> plotJob ) {

        /* Any annotations of the existing plot are out of date and should
         * be cancelled. */
        extraNoteRunner_.cancel( true );
        plotNoteRunner_.cancel( true );

        /* If the previously requested plot has not yet started, simply
         * cancel it.  If it has started, work out whether we want to let
         * it complete or to interrupt it directly.
         * This is not an easy call to make, but interactive response
         * seems to be best served by allowing the previous one to
         * complete if it was "on the way to" the new one - e.g. part of
         * the same (pan, zoom, style slider) drag gesture.  If not,
         * (e.g. different data, new layer) it's better to cancel the
         * previous one directly and start drawing the new one. 
         * Working out if one plot is on the way to another is itself
         * not easy, we delegate it here to the isSimilar method. */
        PlotJobRunner plotRunner = plotRunner_;
        boolean interruptPrevious = ! plotRunner.isSimilar( plotJob );
        plotRunner.cancel( interruptPrevious );

        /* Store such plot surface information as we can calculate fast. */
        latestSurfaces_ = plotJob.getSurfacesQuickly();

        /* Schedule the plot job for execution. */
        plotRunner_ =
            new PlotJobRunner( plotJob, interruptPrevious ? null : plotRunner );
        plotRunner_.submit();
    }

    /**
     * Submits a runnable to run when the plot is not changing.
     * If the plot changes while it's in operation, it will be cancelled.
     * The supplied runnable should watch for thread interruptions.
     * Such runnables are notionally run on a different queue than the
     * one doing the plot.
     *
     * @param  annotator  runnable, typically for annotating the plot
     *                    in some sense
     */
    public void submitExtraAnnotator( Runnable annotator ) {
        extraNoteRunner_.cancel( true );
        extraNoteRunner_ = new Cancellable( noteExec_.submit( annotator ) );
    }

    /**
     * Submits a runnable to run on the same queue as the plot itself.
     * If the plot changes while it's in operation, it will be cancelled.
     * The supplied runnable should watch for thread interruptions.
     * Such runnables are notionally run on the same queue as the one
     * doing the plot, so will only run when a plot is complete.
     * They should use a GuiDataStore such as the one used by
     * {@link #createGuiPointCloud createGuiPointCloud} method
     * so that progress is logged as appropriate.
     *
     * @param  annotator  runnable to run on the plot queue
     */
    public void submitPlotAnnotator( Runnable annotator ) {
        plotNoteRunner_.cancel( true );
        plotNoteRunner_ = new Cancellable( plotExec_.submit( annotator ) );
    }

    /**
     * Returns the data store used in the most recent completed plot.
     *
     * @return  data store
     */
    public DataStore getDataStore() {
        return workings_.dataStore_;
    }

    /**
     * Returns zone arrangement gang for the most recently completed plot.
     *
     * @return   zone gang
     */
    public Gang getGang() {
        return workings_.gang_;
    }

    /**
     * Returns the number of zones in the most recently completed plot.
     *
     * @return   zone count
     */
    public int getZoneCount() {
        return workings_.zones_.length;
    }

    /**
     * Returns the zone index for the surface whose data bounds enclose
     * a given graphics position.  If the position is not within the
     * data bounds of any displayed plot surface, -1 is returned.
     *
     * @param  pos  graphics position to query
     * @return  zone index, or -1
     */
    public int getZoneIndex( Point pos ) {
        Workings w = workings_;
        int iz = w.gang_.getNavigationZoneIndex( pos );
        return iz >= 0 &&
               w.zones_[ iz ].placer_.getSurface().getPlotBounds()
                                                  .contains( pos )
             ? iz
             : -1;
    }

    /**
     * Returns the zone ID of a given zone for the most recently 
     * completed plot.
     *
     * @param   iz   zone index
     * @return  zone id
     */
    public ZoneId getZoneId( int iz ) {
        return workings_.zones_[ iz ].zid_;
    }

    /**
     * Returns the plot surface of a given zone
     * for the most recent completed plot.
     *
     * @param   iz  zone index
     * @return  plot surface
     */
    public Surface getSurface( int iz ) {
        return workings_.zones_[ iz ].placer_.getSurface();
    }

    /**
     * Returns the plot layers painted in a given zone
     * for the most recent completed plot.
     *
     * @param   iz  zone index
     * @return  plot layers
     */
    public PlotLayer[] getPlotLayers( int iz ) {
        return workings_.zones_[ iz ].layers_;
    }

    /**
     * Returns the plot reports for a given zone
     * generated by the most recent completed plot.
     * The array elements correspond to those of the plot layers array.
     *
     * @param   iz  zone index
     * @return   per-layer plot reports
     */
    public ReportMap[] getReports( int iz ) {
        return workings_.zones_[ iz ].reports_;
    }

    /**
     * Returns a point cloud that describes all the point positions included
     * in a given zone for the most recent plot.
     * This contains all the points from all the
     * subsets requested for plotting, including points not visible because
     * they fell outside the plot surface.
     * Iterating over the points described by the returned point cloud,
     * when using the DataStore available from it, takes care of progress
     * updates and thread interruptions.
     *
     * @param   iz  zone index
     * @return  positions in most recent plot
     */
    public GuiPointCloud createGuiPointCloud( int iz ) {
        SubCloud[] subClouds =
            SubCloud.createSubClouds( workings_.zones_[ iz ].layers_, true );
        return new GuiPointCloud( TableCloud.createTableClouds( subClouds ),
                                  getDataStore(),
                                  showProgressModel_.isSelected() ? progModel_
                                                                  : null );
    }

    /**
     * Returns a point cloud like that from {@link #createGuiPointCloud}
     * but for partial positions - ones for which data positions will have
     * one or more missing (NaN) coordinates.
     *
     * @param   iz  zone index
     * @return   partial positions in most recent plot
     * @see  uk.ac.starlink.ttools.plot2.SubCloud#createPartialSubClouds
     */
    public GuiPointCloud createPartialGuiPointCloud( int iz ) {
        SubCloud[] subClouds =
            SubCloud
           .createPartialSubClouds( workings_.zones_[ iz ].layers_, true );
        return new GuiPointCloud( TableCloud.createTableClouds( subClouds ),
                                  getDataStore(),
                                  showProgressModel_.isSelected() ? progModel_
                                                                  : null );
    }

    /**
     * Returns the best guess for the plot surface of a given zone
     * which will be displayed next.
     * It may in fact be the surface for a plot which is
     * currently being calculated.
     *
     * @param   iz  zone index
     * @return   most up-to-date plot surface
     */
    public Surface getLatestSurface( int iz ) {
        return latestSurfaces_[ iz ] != null ? latestSurfaces_[ iz ]
                                             : getSurface( iz );
    }

    /**
     * Clears state to initial values, cancels any plots in progress,
     * and disposes of potentially expensive memory assets.
     */
    public void clearData() {
        plotJob_ = null;
        plotRunner_.cancel( true );
        plotNoteRunner_.cancel( true );
        extraNoteRunner_.cancel( true );
        plotRunner_ = new PlotJobRunner();
        plotNoteRunner_ = new Cancellable();
        extraNoteRunner_ = new Cancellable();
        workings_ = dummyWorkings_;
        navDecoration_ = null;
    }

    /**
     * Sets a list of points which should be highlighted in the plot.
     * This overwrites any previously set highlights map,
     * and triggers a replot.
     * These highlights will be retained for as long as the given
     * data specs are visible.
     *
     * @param  highlightMap  sequence of data positions labelled by SubCloud
     */
    public void setHighlights( Map<SubCloud,double[]> highlightMap ) {
        highlightMap_ = highlightMap;
        replot();
    }

    /**
     * Sets a decoration giving visual feedback for navigation gestures.
     * This decoration will overwrite any previously set value, and will be
     * retained until overwritten with a null value.
     * This method triggers a repaint, but not a replot; the data graphics
     * are assumed to be unaffected.
     *
     * @param  navDec  navigation decoration, or null to erase it
     */
    public void setNavDecoration( Decoration navDec ) {
        navDecoration_ = navDec;
        repaint();
    }

    /**
     * Acquires all the state necessary to define how to perform a plot,
     * and packages it up as an immutable object.
     *
     * @return   new plot job based on current state
     */
    private PlotJob<P,A> createPlotJob() {

        /* Acquire per-panel state. */
        PlotPosition plotpos = posFact_.getItem();
        Rectangle bounds = getOuterBounds( plotpos );
        GraphicsConfiguration graphicsConfig = getGraphicsConfiguration();
        Color bgColor = getBackground();
        Ganger<P,A> ganger = gangerFact_.getItem();
        boolean axisLock = axisLockModel_.isSelected();
        boolean auxLock = auxLockModel_.isSelected();
        ZoneDef<P,A>[] zoneDefs = zonesFact_.getItem();
        int nz = zoneDefs.length;

        /* Get profiles, made consistent across multi-zone plots. */
        List<ConfigMap> surfConfigs = new ArrayList<ConfigMap>();
        List<P> profileList = new ArrayList<P>();
        for ( ZoneDef zoneDef : zoneDefs ) {
            ConfigMap surfConfig = zoneDef.getAxisController().getConfig();
            surfConfigs.add( surfConfig );
            profileList.add( surfFact_.createProfile( surfConfig ) );
        }
        P[] profiles =
            ganger.adjustProfiles( profileList.toArray( profiles0_ ) );

        /* Acquire per-zone state. */
        List<PlotJob.Zone<P,A>> zoneList = new ArrayList<PlotJob.Zone<P,A>>();
        List<SubCloud> allSubclouds = new ArrayList<SubCloud>();
        for ( int iz = 0; iz < nz; iz++ ) {
            ZoneDef zoneDef = zoneDefs[ iz ];
            PlotLayer[] layers = zoneDef.getLayers();
            assert layerListEquals( layers, zoneDef.getLayers() );
            assert layerSetEquals( layers, zoneDef.getLayers() );
            List<SubCloud> subClouds =
                Arrays.asList( SubCloud.createSubClouds( layers, true ) );
            allSubclouds.addAll( subClouds );
            Map<SubCloud,double[]> highMap =
                new HashMap<SubCloud,double[]>( highlightMap_ );
            highMap.keySet().retainAll( subClouds );
            double[][] highlights =
                highMap.values().toArray( new double[ 0 ][] );
            AxisController<P,A> axisController = zoneDef.getAxisController();
            ConfigMap surfConfig = surfConfigs.get( iz );
            P profile = profiles[ iz ];
            axisController.updateState( profile, layers, axisLock );
            A fixAspect = axisController.getAspect();
            Range[] geomFixRanges = axisController.getRanges();
            ShadeAxisFactory shadeFact = zoneDef.getShadeAxisFactory();
            Map<AuxScale,Range> auxFixRanges = new HashMap<AuxScale,Range>();
            Map<AuxScale,Subrange> auxSubranges =
                new HashMap<AuxScale,Subrange>();
            Map<AuxScale,Boolean> auxLogFlags = new HashMap<AuxScale,Boolean>();
            auxFixRanges.put( AuxScale.COLOR, zoneDef.getShadeFixRange() );
            auxSubranges.put( AuxScale.COLOR, zoneDef.getShadeSubrange() );
            auxLogFlags.put( AuxScale.COLOR, zoneDef.isShadeLog() );
            Icon legend = zoneDef.getLegend();
            assert legend == null || zoneDef.getLegend().equals( legend );
            float[] legpos = zoneDef.getLegendPosition();
            String title = zoneDef.getTitle();
            LayerOpt[] opts = PaperTypeSelector.getOpts( layers );
            PaperType paperType =
                ptSel_.getPixelPaperType( opts, compositor_, this );
            ZoneId zid = zoneDef.getZoneId();
            PlotJob.Zone<P,A> zone =
                new PlotJob.Zone<P,A>( layers, profile, fixAspect,
                                       geomFixRanges, surfConfig, shadeFact,
                                       auxFixRanges, auxSubranges, auxLogFlags,
                                       legend, legpos, title, highlights,
                                       paperType, axisController, zid );
            zoneList.add( zone );
        }
        @SuppressWarnings("unchecked")
        PlotJob.Zone<P,A>[] zones =
            (PlotJob.Zone<P,A>[]) zoneList.toArray( new PlotJob.Zone[ 0 ] );

        /* For any of the currently highlighted points, if no layer
         * in any zone contains it, drop that highlight permanently.
         * You could argue that this should be done at plot time rather
         * than at plot job creation, since creating a plot job does
         * not entail that it will ever be plotted, but it's likely
         * that the effect will be the same. */
        highlightMap_.keySet().retainAll( allSubclouds );

        /* Construct and return a plot job that defines what has to be done. */
        return new PlotJob<P,A>( workings_, surfFact_, ganger, zones,
                                 storeFact_, bounds, graphicsConfig,
                                 bgColor, auxLock );
    }

    /**
     * Paints the most recently cached plot icons.
     */
    @Override
    protected void paintComponent( Graphics g ) {
        super.paintComponent( g );
        Insets insets = getInsets();
        Rectangle extBox = null;

        /* Draw the actual pre-calculated plot zone icons. */
        for ( Workings.ZoneWork zone : workings_.zones_ ) {
            Icon plotIcon = zone.plotIcon_;
            if ( plotIcon != null ) {
                plotIcon.paintIcon( this, g, insets.left, insets.top );
            }
            Rectangle zoneExtBounds = zone.placer_.getBounds();
            if ( extBox == null ) {
                extBox = new Rectangle( zoneExtBounds );
            }
            else {
                extBox.add( zoneExtBounds );
            }
        }

        /* Draw a border around the outside of the plot zones.
         * This will normally be invisible, since the plot gang is sized
         * to fit this component.  However, if the size has been set
         * explicitly it's useful to be able to see where the outline is. */
        if ( extBox != null && workings_.dataStore_ != null ) {
            Color color0 = g.getColor();
            g.setColor( Color.GRAY );
            g.drawRect( insets.left - 1, insets.top - 1,
                        extBox.width + 1, extBox.height + 1 );
            g.setColor( color0 );
        }

        /* Draw navigation overlays if any. */
        Decoration navdec = navDecoration_;
        if ( navdec != null ) {
            navdec.paintDecoration( g );
        }
    }

    /**
     * Returns the bounds to use for the plot icon.
     * This includes axis decorations etc, but excludes component insets.
     *
     * @param  plotPos   plot position including explicit settings
     *                   for external dimensions (padding not used here)
     * @return   plot drawing bounds
     */
    private Rectangle getOuterBounds( PlotPosition plotpos ) {
        Integer xpix = plotpos.getWidth();
        Integer ypix = plotpos.getHeight();
        Insets insets = getInsets();
        int x = insets.left;
        int y = insets.top;
        int width = xpix == null ? getWidth() - insets.left - insets.right
                                 : xpix.intValue();
        int height = ypix == null ? getHeight() - insets.top - insets.bottom
                                  : ypix.intValue();
        return new Rectangle( x, y, width, height );
    }

    /**
     * Adds a listener which will be messaged when the content of the
     * displayed plot actually changes.
     *
     * @param  listener   plot change listener
     */
    public void addChangeListener( ChangeListener listener ) {
        changeListenerList_.add( listener );
    }

    /**
     * Removes a listener previously added.
     *
     * @param  listener  plot change listener
     */
    public void removeChangeListener( ChangeListener listener ) {
        changeListenerList_.remove( listener );
    }

    /**
     * Messages change listeners.
     */
    private void fireChangeEvent() {
        ChangeEvent evt = new ChangeEvent( this );
        for ( ChangeListener listener : changeListenerList_ ) {
            listener.stateChanged( evt );
        }
    }

    /**
     * Returns an icon corresponding to the current state of this panel.
     * This method executes quickly, but the returned icon's paintIcon
     * method might take time.  The returned value is immutable,
     * and its behaviour is not affected by subsequent changes to this panel.
     *
     * @param  forceBitmap   true to force bitmap output of vector graphics,
     *                       false to use default behaviour
     * @return  icon
     */
    public Icon createExportIcon( final boolean forceBitmap ) {
        return new ExportIcon( workings_, forceBitmap, ptSel_, compositor_ );
    }

    /**
     * Utility method to return the list of non-null DataSpecs corresponding
     * to a given PlotLayer array.
     * Null dataspecs are ignored, so the output list may not be the same
     * length as the input array.
     *
     * @param   layers   plot layers
     * @return   data spec list
     */
    private static List<DataSpec> getDataSpecs( PlotLayer[] layers ) {
        List<DataSpec> list = new ArrayList<DataSpec>();
        for ( PlotLayer layer : layers ) {
            DataSpec spec = layer.getDataSpec();
            if ( spec != null ) {
                list.add( spec );
            }
        }
        return list;
    }

    /**
     * Returns a list of LayerIds corresponding to an array of plot layers.
     *
     * @param  layers   plot layers
     * @return  list of layer ids
     */
    private static List<LayerId> layerList( PlotLayer[] layers ) {
        List<LayerId> llist = new ArrayList<LayerId>( layers.length );
        for ( PlotLayer layer : layers ) {
            llist.add( LayerId.createLayerId( layer ) );
        }
        return llist;
    }

    /**
     * Returns an identity object representing the parts of a plot job
     * that must be equal between two instances to confer similarity
     * for the purposes of working out whether to interrupt a previous job.
     * This notion of similarity is not very well defined, and may be
     * adjusted in the future; it's a case of trying to get something
     * which works well in the UI.
     *
     * <p>For now two jobs are characterised as similar if they have
     * the same list of layer types, in the same order, with the same
     * data.  However the plot surface and the layer styles may change.
     * This accommodates as similar for instance panned/zoomed versions
     * of the same plot or versions which differ by having the
     * transparency limit adjusted.
     *
     * @param  plotJob  job to identify
     * @return   list by zone of lists of layerIds
     */
    @Equality
    private static <P,A> Object getSimilarityObject( PlotJob<P,A> plotJob ) {
        List<List<LayerId>> listList = new ArrayList<List<LayerId>>();
        if ( plotJob != null ) {
            for ( PlotJob.Zone<P,A> zone : plotJob.zones_ ) {
                listList.add( layerListNoStyle( zone.layers_ ) );
            }
        }
        return listList;
    }

    /**
     * Returns a list of LayerIds corresponding to an array of plot layers,
     * but in which the layer styles are not recorded (are set to null).
     *
     * @param  layers  plot layers
     * @return  list of style-less layer ids
     */
    private static List<LayerId> layerListNoStyle( PlotLayer[] layers ) {
        List<LayerId> llist = new ArrayList<LayerId>();
        if ( layers != null ) {
            for ( PlotLayer layer : layers ) {
                llist.add( new LayerId( layer.getPlotter(), layer.getDataSpec(),
                                        layer.getDataGeom(), (Style) null ) );
            }
        }
        return llist;
    }

    /**
     * Determines whether two ordered lists of layers are effectively
     * identical.
     *
     * @param  layers1   first list
     * @param  layers2   second list
     * @return  true iff both lists are the same
     */
    private static boolean layerListEquals( PlotLayer[] layers1,
                                            PlotLayer[] layers2 ) {
        return layerList( layers1 ).equals( layerList( layers2 ) );
    }

    /**
     * Determines whether two unordered lists of layers contain the
     * equivalent sets of layers.
     *
     * @param  layers1   first list
     * @param  layers2   second list
     * @return   true iff both lists contain the same unique layers
     */
    private static boolean layerSetEquals( PlotLayer[] layers1,
                                           PlotLayer[] layers2 ) {
        return new HashSet<LayerId>( layerList( layers1 ) )
              .equals( new HashSet<LayerId>( layerList( layers2 ) ) );
    }

    /**
     * Constructs a placeholder ZoneWork object.
     *
     * @param  surfFact  surface factory
     * @return   dummy zonework object
     */
    private static <P,A> Workings.ZoneWork<A>
            createDummyZoneWork( SurfaceFactory<P,A> surfFact ) {
        ConfigMap config = new ConfigMap();
        P profile = surfFact.createProfile( config );
        A aspect = surfFact.createAspect( profile, config, null );
        Rectangle box = new Rectangle( 400, 300 );
        Surface surf = surfFact.createSurface( box, profile, aspect );
        PlotPlacement placer = new PlotPlacement( box, surf );
        return new Workings.ZoneWork<A>( new PlotLayer[ 0 ], new Object[ 0 ],
                                         surf, new Range[ 0 ], aspect,
                                         new HashMap<AuxScale,Range>(),
                                         new HashMap<AuxScale,Range>(), false,
                                         placer, (Icon) null, (Icon) null,
                                         new ReportMap[ 0 ], null, null );
    }

    /**
     * Constructs a placeholder workings object.
     * It contains a single dummy zone rather than no zones,
     * for the convenience of single-zone plots who just use zone 0.
     *
     * @param  surfFact  surface factory
     * @return   dummy workings object
     */
    private static <P,A> Workings<A>
            createDummyWorkings( SurfaceFactory<P,A> surfFact ) {
        final Rectangle bounds = new Rectangle( 400, 300 );
        Gang gang = new Gang() {
            public int getNavigationZoneIndex( Point p ) {
                return -1;
            }
            public int getZoneCount() {
                return 1;
            }
            public Rectangle getZonePlotBounds( int iz ) {
                return bounds;
            }
        };
        Workings.ZoneWork[] zones = new Workings.ZoneWork[] {
            createDummyZoneWork( surfFact ),
        };
        return new Workings<A>( gang, zones, bounds, (DataStore) null, 1, 0L );
    }

    /**
     * Immutable object representing the input to and result of a PlotJob.
     * If you've generated a Workings object you have done all the work
     * that can be done outside of the Event Dispatch Thread for making a plot.
     * The workings object also contains information that can be re-used
     * for subsequent plots if the input requirements are sufficiently
     * similar.
     */
    private static class Workings<A> {
        final Gang gang_;
        final ZoneWork<A>[] zones_;
        final Rectangle extBounds_;
        final DataStore dataStore_;
        final int rowStep_;
        final long plotMillis_;

        /**
         * Constructs a fully populated workings object.
         *
         * @param  gang   plot surface gang
         * @param  zones   per-zone working objects
         * @param  extBounds   external bounds
         * @param  dataStore  data storage object
         * @param  rowStep   row stride used for subsample in actual plots
         * @param  plotMillis  wall-clock time in milliseconds taken for the
         *                     plot (plans+paint), but not data acquisition
         */
        Workings( Gang gang, ZoneWork<A>[] zones, Rectangle extBounds,
                  DataStore dataStore, int rowStep, long plotMillis ) {
            gang_ = gang;
            zones_ = zones;
            extBounds_ = extBounds;
            dataStore_ = dataStore;
            rowStep_ = rowStep;
            plotMillis_ = plotMillis;
        }

        @Equality
        List<DataIconId> getDataIconIdList() {
            List<DataIconId> list = new ArrayList<DataIconId>();
            for ( ZoneWork zone : zones_ ) {
                list.add( zone.getDataIconId() );
            }
            return list;
        }

        /**
         * Aggregates per-zone information for a Workings object.
         */
        static class ZoneWork<A> { 
            final PlotLayer[] layers_;
            final Object[] plans_;
            final Surface approxSurf_;
            final Range[] geomRanges_;
            final A aspect_;
            final Map<AuxScale,Range> auxDataRangeMap_;
            final Map<AuxScale,Range> auxClipRangeMap_;
            final boolean auxLock_;
            final PlotPlacement placer_;
            final Icon dataIcon_;
            final Icon plotIcon_;
            final ReportMap[] reports_;
            final AxisController<?,A> axisController_;
            final ZoneId zid_;

            /**
             * Constructor.
             *
             * @param  layers   plot layers
             * @param  plans   per-layer plot plan objects
             * @param  approxSurf   approximation to plot surface
             *                      (size etc may be a bit out)
             * @param  geomRanges   ranges for the geometry coordinates
             * @param  aspect    surface aspect
             * @param  auxDataRangeMap  aux scale ranges derived from data
             * @param  auxClipRangeMap  aux scale ranges derived from
             *                          fixed constraints
             * @param  auxLock  true if aux ranges were held over
             *                  from the previous plot
             * @param  placer  plot placement
             * @param  dataIcon   icon which will paint data part of plot
             * @param  plotIcon   icon which will paint the whole plot
             * @param  reports    reported info from plot layers
             * @param  axisController   axis controller to be updated on
             *                          zone plot completion
             * @param  zid   zone identifier
             */
            ZoneWork( PlotLayer[] layers, Object[] plans,
                      Surface approxSurf, Range[] geomRanges, A aspect,
                      Map<AuxScale,Range> auxDataRangeMap,
                      Map<AuxScale,Range> auxClipRangeMap, boolean auxLock,
                      PlotPlacement placer, Icon dataIcon, Icon plotIcon,
                      ReportMap[] reports,
                      AxisController<?,A> axisController, ZoneId zid ) {
                layers_ = layers;
                plans_ = plans;
                approxSurf_ = approxSurf;
                geomRanges_ = geomRanges;
                aspect_ = aspect;
                auxDataRangeMap_ = auxDataRangeMap;
                auxClipRangeMap_ = auxClipRangeMap;
                auxLock_ = auxLock;
                placer_ = placer;
                dataIcon_ = dataIcon;
                plotIcon_ = plotIcon;
                reports_ = reports;
                axisController_ = axisController;
                zid_ = zid;
            }

            /**
             * Returns an object which characterises the data content of this
             * zone.  Two workings objects which have equal DataIconIds will
             * have equivalent dataIcon members.
             */
            @Equality
            DataIconId getDataIconId() {
                return new DataIconId( placer_.getSurface(), layers_,
                                       auxClipRangeMap_ );
            }

            /**
             * Updates the AxisController GUI component associated with this
             * object.  Should be called (from the Event Dispatch Thread)
             * when a plot has been completed.
             *
             * <p>It's not very nice modifying a GUI component from here.
             * The state has to be updated, because subsequent plot attempts
             * use the state stored there to provide the fixed aspect.
             * But maybe there's a cleaner way?
             */
            void updateAxisController() {
                axisController_.setAspect( aspect_ );
                axisController_.setRanges( geomRanges_ );
            }
        }
    }

    /**
     * Contains all the inputs required to perform a plot and methods to
     * generate a Workings object. 
     */
    private static class PlotJob<P,A> {
       
        private final Workings<A> oldWorkings_;
        private final SurfaceFactory<P,A> surfFact_;
        private final Ganger<P,A> ganger_;
        private final Zone<P,A>[] zones_;
        private final DataStoreFactory storeFact_;
        private final Rectangle extBounds_;
        private final GraphicsConfiguration graphicsConfig_;
        private final Color bgColor_;
        private final boolean auxLock_;
        private final Workings.ZoneWork<A> dummyZoneWork_;

        /**
         * Constructor.
         *
         * @param   oldWorkings  workings object from a previous run;
         *                       parts of it may be re-used where appropriate
         * @param   surfFact  surface factory
         * @param   ganger     defines how multi-zone plots are grouped
         * @param   zones    per-zone plot information
         * @param   storeFact  data store factory implementation
         * @param   extBounds   external bounds for entire plot display
         * @param   graphicsConfig  graphics configuration
         * @param   bgColor   background colour
         * @param   auxLock  true if the aux ranges are to be held over
         *                     from the previous plot; false if they may
         *                     need to be recalculated
         */
        PlotJob( Workings<A> oldWorkings, SurfaceFactory<P,A> surfFact,
                 Ganger<P,A> ganger, Zone<P,A>[] zones,
                 DataStoreFactory storeFact, Rectangle extBounds,
                 GraphicsConfiguration graphicsConfig, Color bgColor,
                 boolean auxLock ) {
            oldWorkings_ = oldWorkings;
            surfFact_ = surfFact;
            ganger_ = ganger;
            zones_ = zones;
            storeFact_ = storeFact;
            extBounds_ = extBounds;
            graphicsConfig_ = graphicsConfig;
            bgColor_ = bgColor;
            auxLock_ = auxLock;
            dummyZoneWork_ = createDummyZoneWork( surfFact );
        }

        /**
         * Calculates the workings object.
         * In case of error, or if the plot would have been just
         * the same as the previously calculated one (from oldWorkings),
         * null is returned.
         *
         * @param  rowStep  stride for selecting row subsample; 1 means all rows
         * @param  progModel   progress bar model to be updated with progress;
         *                     if null, progress is not logged
         * @return  workings object or null
         */
        @Slow
        public Workings<A> calculateWorkings( int rowStep,
                                              BoundedRangeModel progModel ) {
            try {
                return attemptCalculateWorkings( rowStep, progModel );
            }
            catch ( InterruptedException e ) {
                Thread.currentThread().interrupt();
                return null;
            }
            catch ( IOException e ) {
                logger_.log( Level.WARNING, "Plot data error: " + e, e );
                return null;
            }
            catch ( OutOfMemoryError e ) {
                TopcatUtils.memoryErrorLater( e );
                return null;
            }
            catch ( Throwable e ) {
                logger_.log( Level.WARNING, "Plot data error: " + e, e );
                return null;
            }
        }

        /**
         * Attempts to calculate the workings object for this job.
         * If the plot would have been just the same as the one
         * previously calculated (from oldWorkings), null is returned
         *
         * @param  rowStep  stride for selecting row subsample; 1 means all rows
         * @param  progModel   progress bar model to be updated with progress;
         *                     if null, progress is not logged
         * @return   workings object, or null
         * @throws   IOException  in case of IO error
         * @throws   InterruptedException   if interrupted
         */
        @Slow
        private Workings<A>
                attemptCalculateWorkings( int rowStep,
                                          BoundedRangeModel progModel )
                throws IOException, InterruptedException {
            if ( extBounds_.width > 0 && extBounds_.height > 0 ) {
                DataStore dataStore = readDataStore();
                long ntuple = progModel == null
                            ? -1
                            : countTuples( dataStore, rowStep );
                return createWorkings( dataStore, rowStep, progModel, ntuple );
            }
            else {
                return null;
            }
        }

        /**
         * Counts how many tuples will be read in total when performing
         * this plot.
         *
         * @param   dataStore  contains the data for the plot
         * @param   rowStep  stride for subsampling rows
         * @return   total tuples expected to be read by a real plot,
         *           or -1 if not known
         */
        @Slow
        private long countTuples( DataStore dataStore, int rowStep ) {

            /* Set up a dummy data store based on this one, capable of
             * counting how many tuples are required. */
            CountDataStore countStore = new CountDataStore( dataStore, 8 );
            long cStart = System.currentTimeMillis();

            /* Do a dummy plot using that data store. */
            if ( createWorkings( countStore, rowStep, null, -1 ) != null ) {
                PlotUtil.logTime( logger_, "CountProgress", cStart );

                /* If successful, interrogate the data store for the number
                 * of tuples that [would have] got read. */
                return countStore.getTupleCount();
            }

            /* In case of trouble, return an indeterminate result. */
            else {
                return -1L;
            }
        }

        /**
         * Return a data store that can be used for performing this plot.
         * It may be possible to reuse one from last time (the cached
         * workings object), but if not, read a new one.
         *
         * @return  data store usable for this plot
         */
        @Slow
        private DataStore readDataStore()
                throws IOException, InterruptedException {

            /* Assess what data specs we will need. */
            List<DataSpec> dataSpecList = new ArrayList<DataSpec>();
            for ( Zone zone : zones_ ) {
                dataSpecList.addAll( getDataSpecs( zone.layers_ ) );
            }
            DataSpec[] dataSpecs = dataSpecList.toArray( new DataSpec[ 0 ] );

            /* If the oldWorkings data store contains the required data,
             * use that. */
            DataStore oldDataStore = oldWorkings_.dataStore_;
            if ( hasData( oldDataStore, dataSpecs ) ) {
                return oldDataStore;
            }

            /* Otherwise need a new data store. */
            else {
                long startData = System.currentTimeMillis();
                DataStore dataStore =
                    storeFact_.readDataStore( dataSpecs, oldDataStore );
                PlotUtil.logTime( logger_, "Data", startData );
                return dataStore;
            }
        }

        /**
         * Do the actual work for plotting.  This method
         * creates and returns a Workings object containing
         * the plot icon along with a load of intermediate information
         * calculated along the way that may be useful next time.
         * This method has no side-effects.
         *
         * <p>A null return indicates that there is no updated workings
         * object.  That could be either because the workings object
         * can be determined to be the same as the old one, or because
         * the calculations were interrupted.
         *
         * @param  dataStore  data store
         * @param  rowStep   stride for row subsampling, 1 for all rows
         * @param  progModel  progress bar model to update as tuples are read,
         *                    or null for no progress updates
         * @param  ntuple   total tuple count expected; used only for progress
         *                  updates, -1 if not known
         * @return  workings object representing completed plot, or null
         */
        @Slow
        private Workings<A> createWorkings( DataStore dataStore, int rowStep,
                                            final BoundedRangeModel progModel,
                                            long ntuple ) {
            long startPlot = System.currentTimeMillis();
   
            /* Record the base data store which will be stored in the
             * output workings object, and prepare a data store decorated
             * with various wrappers to use for the actual processing. */
            DataStore dataStore0 = dataStore;
            DataStore dataStore1 = dataStore;
            dataStore = null;

            /* Pick subsample of rows if requested. */
            if ( rowStep > 1 ) {
                dataStore1 = new StepDataStore( dataStore1, rowStep );
            }

            /* Arrange for progress logging.  This also ensures that
             * if the thread is interrupted, tuples are no longer dispensed
             * from the data store.  We still have to check for thread
             * interruption status periodically, i.e. after every use of
             * the data store. */
            dataStore1 =
                new GuiDataStore( dataStore1, progModel, ntuple / rowStep );

            /* Before we can work out the gang geometry, we need the
             * surface aspects and shade axes for each zone.
             * Calculate those, and retain other intermediate results
             * we'll need later that we generate along the way. */
            int nz = zones_.length;
            A[] aspects = PlotUtil.createAspectArray( surfFact_, nz );
            Range[][] geomRanges = new Range[ nz ][];
            long startRange = System.currentTimeMillis();
            for ( int iz = 0; iz < nz; iz++ ) {
                Zone<P,A> zone = zones_[ iz ];

                /* Ascertain the surface aspect.  If it has been set
                 * explicitly, use that.  The fixAspect can come indirectly
                 * a previous invocation of this method. */
                final A aspect;
                final Range[] gRanges;
                if ( zone.fixAspect_ != null ) {
                    aspect = zone.fixAspect_;
                    gRanges = zone.geomFixRanges_;
                }

                /* Otherwise work it out from the supplied config and
                 * by scanning the data if necessary. */
                else {
                    if ( zone.geomFixRanges_ != null ) {
                        gRanges = zone.geomFixRanges_;
                    }
                    else if ( ! surfFact_.useRanges( zone.profile_,
                                                     zone.aspectConfig_ ) ) {
                        gRanges = null;
                    }
                    else {
                        gRanges =
                            surfFact_.readRanges( zone.profile_, zone.layers_,
                                                  dataStore1 );
                        if ( Thread.currentThread().isInterrupted() ) {
                            return null;
                        }
                        // could cache the ranges here by point cloud ID
                        // for possible later use.
                        // That would be an especially good idea for
                        // multi-zone plots.
                    }
                    aspect = surfFact_.createAspect( zone.profile_,
                                                     zone.aspectConfig_,
                                                     gRanges );
                }

                /* Store results for later. */
                aspects[ iz ] = aspect;
                geomRanges[ iz ] = gRanges;
            }
            PlotUtil.logTime( logger_, "Range", startRange );
            aspects = ganger_.adjustAspects( aspects, -1 );

            /* Collect previously calculated plans, which may be able to
             * supply results required this time round and thus avoid
             * some recalculations. */
            Set oldPlans = new HashSet();
            for ( Workings.ZoneWork<A> zone : oldWorkings_.zones_ ) {
                oldPlans.addAll( Arrays.asList( zone.plans_ ) );
            }

            /* Work out gang geometry if we can (probably not). */
            Gang gang = usesShadeAxes()
                      ? null
                      : createGang( aspects, new ShadeAxis[ nz ] );

            /* In any case, work out an approximate gang geometry we can
             * use for ranging.  Since we don't have shade axes yet,
             * we may have to behave as if there aren't any - it shouldn't
             * make too much difference for this purpose, it just affects
             * the amount of space available for the rest of the plot
             * by a smallish proportion. */
            Gang approxGang = gang != null
                            ? gang
                            : createGang( aspects, new ShadeAxis[ nz ] );

            /* Now we can work out shade axes, along with other intermediate
             * results. */
            ShadeAxis[] shadeAxes = new ShadeAxis[ nz ];
            Surface[] approxSurfs = new Surface[ nz ];
            Map<AuxScale,Range>[] auxDataRangeMaps =
                (Map<AuxScale,Range>[]) new Map[ nz ];
            Map<AuxScale,Range>[] auxClipRangeMaps =
                (Map<AuxScale,Range>[]) new Map[ nz ];
            long startAux = System.currentTimeMillis();
            for ( int iz = 0; iz < nz; iz++ ) {
                Zone<P,A> zone = zones_[ iz ];
                Workings.ZoneWork<A> oldZoneWork =
                      iz < oldWorkings_.zones_.length
                    ? oldWorkings_.zones_[ iz ]
                    : dummyZoneWork_;

                /* Work out the required aux scale ranges.
                 * First find out which ones we need. */
                AuxScale[] scales = AuxScale.getAuxScales( zone.layers_ );

                /* The approxSurf records the basic requirements for
                 * the surface as known at range time: profile, aspect and
                 * bounding box.  The actual surface may be a bit different
                 * because insets are dependent on actual tick placement,
                 * aux colour ramp etc, not known yet. */
                Surface approxSurf =
                    surfFact_.createSurface( approxGang.getZonePlotBounds( iz ),
                                             zone.profile_, aspects[ iz ] );

                /* See if we can re-use the aux ranges from the oldWorkings.
                 * We can if both the approxSurf and the layers are the same
                 * as last time, or if range-locking is in effect. */
                boolean hasSameSurface =
                    approxSurf.equals( oldZoneWork.approxSurf_ );
                final boolean reuseAuxRanges;
                if ( auxLock_ ) {
                    reuseAuxRanges = true;
                }
                else if ( oldZoneWork.auxLock_ ) {
                    reuseAuxRanges = false;
                }
                else {
                    reuseAuxRanges = hasSameSurface
                                  && layerListEquals( zone.layers_,
                                                      oldZoneWork.layers_ );
                }
                Map<AuxScale,Range> auxDataRangeMap =
                    reuseAuxRanges ? oldZoneWork.auxDataRangeMap_
                                   : new HashMap<AuxScale,Range>();

                /* Work out which scales we are going to have to calculate,
                 * if any, and calculate them. */
                AuxScale[] calcScales =
                    AuxScale.getMissingScales( scales, auxDataRangeMap,
                                               zone.auxFixRanges_ );
                if ( calcScales.length > 0 ) {

                    /* If we have to do some ranging work, we need to supply
                     * a surface to do it with.  If possible, use the actual
                     * surface that was used for the previous plot.
                     * In some cases, layers may be able to use the cached
                     * plotting plans (also passed on) to avoid re-calculating
                     * ranges.  If we passed on approxSurf which is similar to,
                     * but not quite the same as, the last plotted surface,
                     * it would be hard for layers to match it with
                     * a cached plan.
                     * However, if we don't have a previous surface that's
                     * basically similar to the current one, we have to use
                     * the approxSurf anyway. */
                    Surface rangeSurf = hasSameSurface
                                      ? oldZoneWork.placer_.getSurface()
                                      : approxSurf;
                    Map<AuxScale,Range> calcRangeMap =
                        AuxScale.calculateAuxRanges( calcScales, zone.layers_,
                                                     rangeSurf,
                                                     oldPlans.toArray(),
                                                     dataStore1 );
                    if ( Thread.currentThread().isInterrupted() ) {
                        return null;
                    }
                    auxDataRangeMap.putAll( calcRangeMap );
                }

                /* Combine available aux scale information to get the
                 * actual ranges for use in the plot. */
                Map<AuxScale,Range> auxClipRangeMap =
                    AuxScale.getClippedRanges( scales, auxDataRangeMap,
                                               zone.auxFixRanges_,
                                               zone.auxSubranges_,
                                               zone.auxLogFlags_ );

                /* Extract and use colour scale range for the shader. */
                Range shadeRange = auxClipRangeMap.get( AuxScale.COLOR );
                ShadeAxis shadeAxis =
                    zone.shadeFact_.createShadeAxis( shadeRange );

                /* Store results for later. */
                shadeAxes[ iz ] = shadeAxis;
                approxSurfs[ iz ] = approxSurf;
                auxDataRangeMaps[ iz ] = auxDataRangeMap;
                auxClipRangeMaps[ iz ] = auxClipRangeMap;
            }
            PlotUtil.logTime( logger_, "AuxRange", startAux );

            /* Now we have enough information for the actual gang geometry. */
            if ( gang == null ) {
                gang = createGang( aspects, shadeAxes );
            }

            /* Now we have enough information to create the actual surfaces,
             * plan and plot the layers onto them, and save the result into
             * per-zone ZoneWork objects. */
            long planMillis = 0;
            long paintMillis = 0;
            Workings.ZoneWork<A>[] zoneWorks =
                (Workings.ZoneWork<A>[]) new Workings.ZoneWork[ nz ];
            boolean changed = false;
            for ( int iz = 0; iz < nz; iz++ ) {
                Zone<P,A> zone = zones_[ iz ];
                Workings.ZoneWork<A> oldZoneWork =
                      iz < oldWorkings_.zones_.length
                    ? oldWorkings_.zones_[ iz ]
                    : dummyZoneWork_;

                /* Aux range locking is in principle handled on a per-zone
                 * basis, but for now the GUI doesn't support that. */
                boolean auxLock = auxLock_;

                /* Calculate the plot surface. */
                Rectangle dataBounds = gang.getZonePlotBounds( iz );
                Surface surface =
                    surfFact_.createSurface( dataBounds, zone.profile_,
                                             aspects[ iz ] );

                /* Get the basic plot decorations. */
                Decoration[] basicDecs =
                    PlotPlacement
                   .createPlotDecorations( surface, zone.legend_, zone.legpos_,
                                           zone.title_, shadeAxes[ iz ] );
                List<Decoration> decList = new ArrayList<Decoration>();
                decList.addAll( Arrays.asList( basicDecs ) );

                /* Place highlighted point icons as further plot decorations. */
                Icon highIcon = HIGHLIGHTER;
                int xoff = highIcon.getIconWidth() / 2;
                int yoff = highIcon.getIconHeight() / 2;
                Point2D.Double gp = new Point2D.Double();
                for ( double[] highlight : zone.highlights_ ) {
                    if ( surface.dataToGraphics( highlight, true, gp ) ) {
                        int gx = PlotUtil.ifloor( gp.x - xoff );
                        int gy = PlotUtil.ifloor( gp.y - yoff );
                        decList.add( new Decoration( highIcon, gx, gy ) );
                    }
                }

                /* Construct the plot placement. */
                Decoration[] decs = decList.toArray( new Decoration[ 0 ] );
                PlotPlacement placer =
                    new PlotPlacement( extBounds_, surface, decs );
                assert placer
                      .equals( new PlotPlacement( extBounds_, surface, decs ) );

                /* Determine whether first the data part, then the entire
                 * graphics, of the plot is the same as for the oldWorkings.
                 * If so, it's likely that we've got this far without any
                 * expensive calculations (data scans), since the ranges
                 * will have been picked up from the previous plot. */
                boolean sameDataIcon =
                    new DataIconId( surface, zone.layers_,
                                    auxClipRangeMaps[ iz ] )
                   .equals( oldZoneWork.getDataIconId() );
                boolean samePlot =
                    sameDataIcon && placer.equals( oldZoneWork.placer_ );

                /* If the plot is identical to last time, store a null
                 * zone workings object an indication that no replot
                 * is required. */
                final Workings.ZoneWork<A> zoneWork;
                if ( samePlot ) {
                    zoneWork = oldZoneWork;
                }

                /* Otherwise, we need to do at least some work. */
                else {
                    changed = true;

                    /* If the data part is the same as last time, no need to
                     * redraw the data icon or recalculate the plans - carry
                     * them forward from the oldWorkings for the result. */
                    final Icon dataIcon;
                    final Object[] plans;
                    final ReportMap[] reports;
                    if ( sameDataIcon ) {
                        dataIcon = oldZoneWork.dataIcon_;
                        plans = oldZoneWork.plans_;
                        reports = oldZoneWork.reports_;
                    }

                    /* Otherwise calculate plans and perform drawing to a new
                     * cached data icon (image buffer). */
                    else {
                        PlotLayer[] layers = zone.layers_;
                        int nl = layers.length;
                        Map<AuxScale,Range> auxRangeMap =
                            auxClipRangeMaps[ iz ];
                        long startPlan = System.currentTimeMillis();
                        Drawing[] drawings = new Drawing[ nl ];
                        for ( int il = 0; il < nl; il++ ) {
                            drawings[ il ] =
                                layers[ il ]
                               .createDrawing( surface, auxRangeMap,
                                               zone.paperType_ );
                        }
                        plans = calculateDrawingPlans( drawings, dataStore1,
                                                       oldPlans );
                        if ( Thread.currentThread().isInterrupted() ) {
                            return null;
                        }
                        planMillis += System.currentTimeMillis() - startPlan;
                        logger_.info( "Zone: "+ iz + " - "
                                    + "Layers: " + nl + ", "
                                    + "Paper: " + zone.paperType_ );
                        reports = new ReportMap[ nl ];
                        for ( int il = 0; il < nl; il++ ) {
                            reports[ il ] =
                                drawings[ il ].getReport( plans[ il ] );
                        }
                        long startPaint = System.currentTimeMillis();
                        dataIcon = zone.paperType_
                                  .createDataIcon( surface, drawings, plans,
                                                   dataStore1, true );
                        paintMillis += System.currentTimeMillis() - startPaint;
                        if ( Thread.currentThread().isInterrupted() ) {
                            return null;
                        }
                    }

                    /* Create the final plot icon, and store the inputs and
                     * outputs as a new Workings object for return. */
                    Icon plotIcon = placer.createPlotIcon( dataIcon );
                    zoneWork =
                        new Workings.ZoneWork<A>( zone.layers_, plans,
                                                  approxSurfs[ iz ],
                                                  geomRanges[ iz ],
                                                  aspects[ iz ],
                                                  auxDataRangeMaps[ iz ],
                                                  auxClipRangeMaps[ iz ],
                                                  auxLock,
                                                  placer, dataIcon, plotIcon,
                                                  reports, zone.axisController_,
                                                  zone.zid_ );
                }
                zoneWorks[ iz ] = zoneWork;
            }
            long now = System.currentTimeMillis();
            PlotUtil.logTime( logger_, "Plan", now - planMillis );
            PlotUtil.logTime( logger_, "Paint", now - paintMillis );
            long plotMillis = now - startPlot;

            /* Construct and return an object containing the workings
             * for all zones, unless it's exactly the same as last time,
             * in which case return null to indicate that no replotting
             * needs to be done.. */
            return changed ? new Workings<A>( gang, zoneWorks, extBounds_,
                                              dataStore0, rowStep, plotMillis )
                           : null;
        }

        /**
         * Attempts to return the plot surfaces for each zone
         * which the result of this job will display.
         * For any surface whose identity cannot be determined quickly
         * (that is, if the data needs to be scanned), null will be
         * given in the corresponding element of the returned array.
         *
         * @return   per-zone array of plot surfaces used for this plot job,
         *           elements are null if they cannot be determined cheaply
         */
        public Surface[] getSurfacesQuickly() {

            /* Implementation follows that of the relevant parts of
             * createWorkings.  Any step that would require a scan through
             * the data means that the corresponding surface is not
             * calculated (set to null). */

            /* First try to work out the aspects. */
            int nz = zones_.length;
            A[] aspects = PlotUtil.createAspectArray( surfFact_, nz );
            boolean hasAllAspects = true;
            for ( int iz = 0; iz < nz; iz++ ) {
                Zone<P,A> zone = zones_[ iz ];
                final A aspect;
                if ( zone.fixAspect_ != null ) {
                    aspect = zone.fixAspect_;
                }
                else {
                    final boolean needsRanging;
                    final Range[] geomRanges;
                    if ( zone.geomFixRanges_ != null ) {
                        geomRanges = zone.geomFixRanges_;
                        needsRanging = false;
                    }
                    else if ( ! surfFact_.useRanges( zone.profile_,
                                                     zone.aspectConfig_ ) ) {
                        geomRanges = null;
                        needsRanging = false;
                    }
                    else {
                        geomRanges = null;
                        needsRanging = true;
                    }
                    aspect = needsRanging
                           ? null
                           : surfFact_.createAspect( zone.profile_,
                                                     zone.aspectConfig_,
                                                     geomRanges );
                }
                hasAllAspects = hasAllAspects && aspect != null;
                aspects[ iz ] = aspect;
            }

            /* Then try to work out the gang. */
            final Gang gang = hasAllAspects && ! usesShadeAxes()
                            ? createGang( aspects, new ShadeAxis[ nz ] )
                            : null;

            /* If we have got the gang, we can calculate the surfaces.
             * Otherwise, return an array with all null elements. */
            Surface[] surfs = new Surface[ nz ];
            if ( gang != null ) {
                for ( int iz = 0; iz < nz; iz++ ) {
                    if ( aspects[ iz ] != null ) {
                        surfs[ iz ] =
                            surfFact_
                           .createSurface( gang.getZonePlotBounds( iz ),
                                           zones_[ iz ].profile_,
                                           aspects[ iz ] );
                    }
                }
            }
            return surfs;
        }

        /**
         * Indicates whether any of the zones contained in this PlotJob
         * will display a shade axis.  If not, gang determination can be
         * done without needing to range the aux coordinate data.
         *
         * @return   true if any zones use, or may use, a shade axis
         */
        private boolean usesShadeAxes() {
            for ( Zone<P,A> zone : zones_ ) {
                if ( zone.shadeFact_
                         .createShadeAxis( new Range( 1, 2 ) ) != null ) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Constructs the gang used by this job given per-zone aspect and
         * shade axis arrays.
         *
         * @param  aspects  per-zone aspect array
         * @param  shadeAxes  per-zone shade axis array
         * @return  surface positioning gang
         */
        private Gang createGang( A[] aspects, ShadeAxis[] shadeAxes ) {
            int nz = zones_.length;
            ZoneContent[] zoneContents = new ZoneContent[ nz ];
            P[] profiles = PlotUtil.createProfileArray( surfFact_, nz );
            for ( int iz = 0; iz < nz; iz++ ) {
                Zone<P,A> zone = zones_[ iz ];
                profiles[ iz ] = zone.profile_;
                zoneContents[ iz ] =
                    new ZoneContent( zone.layers_, zone.legend_,
                                     zone.legpos_, zone.title_ );
	    }
            return ganger_.createGang( extBounds_, surfFact_, nz,
                                       zoneContents, profiles, aspects,
                                       shadeAxes, WITH_SCROLL );
        }

        /**
         * Determines whether a data store has data backing all of a
         * set of data specs.
         *
         * @param   dstore  data store, may be null
         * @param   dspecs  list of data specs
         * @return  true iff the data store has all the specified data
         */
        private static boolean hasData( DataStore dstore, DataSpec[] dspecs ) {
            if ( dstore == null ) {
                return dspecs.length == 0;
            }
            else {
                for ( DataSpec dspec : dspecs ) {
                    if ( dspec != null && ! dstore.hasData( dspec ) ) {
                        return false;
                    }
                }
                return true;
            }
        }

        /**
         * Determines whether an Insets object contains full inset information.
         *
         * @param  insets   represents explicit inset settings
         * @return  true iff insets is not null and all its members are &gt;=0
         */
        private static boolean isFixedInsets( Insets insets ) {
            return insets != null
                && insets.top >= 0
                && insets.left >= 0
                && insets.bottom >= 0
                && insets.right >= 0;
        }

        /**
         * Calculates plot plans for a set of drawings, attempting to re-use
         * previously calculated plans where possible.
         *
         * @param  drawings   drawings
         * @param  dataStore   data storage object
         * @param  oldPlans  unordered collection of plan objects previously
         *                   calculated that may or may not be re-usable
         *                   for the current drawings
         * @return  array of per-drawing plans
         */
        private static Object[] calculateDrawingPlans( Drawing[] drawings,
                                                       DataStore dataStore,
                                                       Collection oldPlans ) {
            int nl = drawings.length;
            Set knownPlans = new HashSet( oldPlans );
            Object[] plans = new Object[ nl ];
            for ( int il = 0; il < nl; il++ ) {
                Object plan =
                    drawings[ il ].calculatePlan( knownPlans.toArray(),
                                                  dataStore );
                plans[ il ] = plan;
                knownPlans.add( plan );
            }
            return plans;
        }

        /**
         * Aggregates per-zone information for a PlotJob.
         */
        static class Zone<P,A> {
            final PlotLayer[] layers_;
            final P profile_;
            final A fixAspect_;
            final Range[] geomFixRanges_;
            final ConfigMap aspectConfig_;
            final ShadeAxisFactory shadeFact_;
            final Map<AuxScale,Range> auxFixRanges_;
            final Map<AuxScale,Subrange> auxSubranges_;
            final Map<AuxScale,Boolean> auxLogFlags_;
            final Icon legend_;
            final float[] legpos_;
            final String title_;
            final double[][] highlights_;
            final PaperType paperType_;
            final AxisController<P,A> axisController_;
            final ZoneId zid_;

            /**
             * Constructor.
             *
             * @param   layers  plot layer array
             * @param   profile   surface profile
             * @param   fixAspect   exact surface aspect, or null if not known
             * @param   geomFixRanges  data ranges for geometry coordinates,
             *                         if known, else null
             * @param   aspectConfig  config map containing aspect keys
             * @param   shadeFact   shader axis factory
             * @param   auxFixRanges  fixed ranges for aux scales, where known
             * @param   auxSubranges  subranges for aux scales, where present
             * @param   auxLogFlags  logarithmic scale flags for aux scales
             *                       (either absent or false means linear)
             * @param   legend   legend icon, or null
             * @param   legpos   legend position as (x,y) array of
             *                   relative positions (0-1),
             *                   or null if legend absent/external
             * @param   title    plot title, or null
             * @param   highlights  array of highlight data positions
             * @param   paperType   rendering implementation
             * @param   axisController  GUI component corresponding to this zone
             * @param   zid   zone identifier
             */
            Zone( PlotLayer[] layers, P profile, A fixAspect,
                  Range[] geomFixRanges, ConfigMap aspectConfig,
                  ShadeAxisFactory shadeFact,
                  Map<AuxScale,Range> auxFixRanges,
                  Map<AuxScale,Subrange> auxSubranges,
                  Map<AuxScale,Boolean> auxLogFlags,
                  Icon legend, float[] legpos, String title,
                  double[][] highlights, PaperType paperType,
                  AxisController<P,A> axisController, ZoneId zid ) {
                layers_ = layers;
                profile_ = profile;
                fixAspect_ = fixAspect;
                geomFixRanges_ = geomFixRanges;
                aspectConfig_ = aspectConfig;
                shadeFact_ = shadeFact;
                auxFixRanges_ = auxFixRanges;
                auxSubranges_ = auxSubranges;
                auxLogFlags_ = auxLogFlags;
                legend_ = legend;
                legpos_ = legpos;
                title_ = title;
                highlights_ = highlights;
                paperType_ = paperType;
                axisController_ = axisController;
                zid_ = zid;
            }
        }
    }

    /**
     * Identifier object for data icon content.
     * Two Workings objects that have the same DataIconId will have
     * the same data icon.
     */
    @Equality
    private static class DataIconId {
        private final Surface surface_;
        private final PlotLayer[] layers_;
        private final Map<AuxScale,Range> auxClipRangeMap_;

        /**
         * Constructor.
         *
         * @param  surface  plot surface
         * @param  layers   plot layers
         * @param  auxClipRangeMap   actual ranges used for aux scales
         */
        DataIconId( Surface surface, PlotLayer[] layers,
                    Map<AuxScale,Range> auxClipRangeMap ) {
            surface_ = surface;
            layers_ = layers;
            auxClipRangeMap_ = auxClipRangeMap;
        }

        public boolean equals( Object o ) {
            if ( o instanceof DataIconId ) {
                DataIconId other = (DataIconId) o;
                return this.surface_.equals( other.surface_ )
                    && layerListEquals( this.layers_, other.layers_ )
                    && this.auxClipRangeMap_.equals( other.auxClipRangeMap_ );
            }
            else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int code = 987;
            code = 23 * code + surface_.hashCode();
            code = 23 * code + layerList( layers_ ).hashCode();
            code = 23 * code + auxClipRangeMap_.hashCode();
            return code;
        }
    }

    /**
     * Stores a reference to a Future in such a way that it can be
     * cancelled if it still exists, but does not prevent it from being GCd.
     */
    private static class Cancellable {
        private final Reference<Future<?>> ref_;

        /**
         * Constructor.
         *
         * @param  future   future object to wrap
         */
        Cancellable( Future<?> future ) {
            ref_ = new WeakReference<Future<?>>( future );
        }

        /**
         * Constructs a dummy cancellable.
         */
        Cancellable() {
            this( null );
        }

        /**
         * Cancels this object's future task if it still exists.
         *
         * @param  mayInterruptIfRunning  whether interruption should take
         *         place if the thing has already started
         */
        public void cancel( boolean mayInterruptIfRunning ) {
            Future<?> future = ref_.get();
            if ( future != null ) {
                future.cancel( mayInterruptIfRunning );
            }
        }
    }

    /**
     * Handles submission and cancelling of plot jobs.
     */
    private class PlotJobRunner {
        private final Object simObj_;
        private final int rowStep_;
        private PlotJob plotJob_;
        private volatile Cancellable fullCanceler_;
        private volatile Cancellable stepCanceler_;
        private volatile long fullPlotMillis_;
        private static final int MAX_FULL_PLOT_MILLIS = 250;
        private static final int MAX_STEP_PLOT_MILLIS = 100;

        /**
         * Constructor.
         *
         * <p>A parameter supplies a previously submitted job
         * for reference; it should have been plotting about the same
         * amount of data and take about the same amount of time.  This is
         * used to work out what step to take for subsampled intermediate
         * plots.  The criteria are not actually the same as for the
         * similarity object, but it's an OK approximation.
         *
         * <p>Reference to the supplied <code>plotJob</code> object will
         * be released as soon as possible, so retaining a reference to
         * this PlotJobRunner is not harmful.
         *
         * @param   plotJob  plot job to be plotted
         * @param   referenceRunner   an instance for a previously submitted
         *          job related to this one; null if none is available
         */
        public PlotJobRunner( PlotJob plotJob, PlotJobRunner refRunner ) {
            plotJob_ = plotJob;
            simObj_ = getSimilarityObject( plotJob );
            assert simObj_.equals( getSimilarityObject( plotJob ) );
            rowStep_ = sketchModel_.isSelected() ? getRowStep( refRunner ) : 1;
        }

        /**
         * Dummy constructor for placeholder instance.
         */
        public PlotJobRunner() {
            this( null, null );
        }

        /**
         * Determines an appropriate subsample step to use given a
         * plot runner that has (maybe) already executed.
         * The subsample step is chosen so that the intermediate plots
         * ought to take a reasonable amount of time.  
         * If in doubt, 1 is returned, which means no subsampling
         * (full plot only).
         *
         * @param  other  runner supplied for reference, or null
         * @return   suitable step for a subsampled plot
         */
        private final int getRowStep( PlotJobRunner other ) {

            /* No reference plot, no subsampling. */
            if ( other == null ) {
                return 1;
            }

            /* If the reference plot has completed a full plot, use the
             * timing for that to work out what to do. */
            else if ( other.fullPlotMillis_ > 0 ) {

                /* If a full plot takes less than a given threshold, don't
                 * subsample. */
                long plotMillis = other.fullPlotMillis_;
                if ( plotMillis <= MAX_FULL_PLOT_MILLIS ) {
                    return 1;
                }

                /* If it takes longer, arrange for a subsample that should
                 * take around a given limit. */
                else {
                    return (int) Math.min( Integer.MAX_VALUE,
                                           plotMillis / MAX_STEP_PLOT_MILLIS );
                }
            }

            /* Otherwise, copy the step of the reference plot. */
            else if ( other.rowStep_ > 1 ) {
                return other.rowStep_;
            }
            else {
                return 1;
            }
        }

        /**
         * Submits this object's job for execution.
         */
        public void submit() {
            if ( fullCanceler_ != null ) {
                throw new IllegalStateException( "Don't call it twice" );
            }
            final PlotJob plotJob = plotJob_;

            /* Void the reference to the plot job so it can be GC'd as
             * early as possible. */
            plotJob_ = null;

            /* Set up runnables to execute the full plot or a subsample plot. */
            final BoundedRangeModel progModel =
                showProgressModel_.isSelected() ? progModel_ : null;
            Runnable fullJob = new Runnable() {
                public void run() {
                    Workings<A> workings =
                        plotJob.calculateWorkings( 1, progModel );
                    fullPlotMillis_ = workings.plotMillis_;
                    submitWorkings( workings );
                }
            };
            Runnable stepJob = new Runnable() {
                public void run() {
                    Workings<A> workings =
                        plotJob.calculateWorkings( rowStep_, null );
                    submitWorkings( workings );
                }
            };

            /* Submit one or both for execution. */
            if ( rowStep_ > 1 ) {
                logger_.info( "Intermediate plot with row step " + rowStep_ );
                stepCanceler_ = new Cancellable( plotExec_.submit( stepJob ) );
            }
            fullCanceler_ = new Cancellable( plotExec_.submit( fullJob ) );
        }

        /**
         * Cancels this object's job if applicable.
         * A parameter indicates whether the next job to be submitted
         * (the one in favour of which this one is being cancelled)
         * is similar to this one.  This may have implications for how
         * aggressively the cancellation is applied.
         *
         * @param  nextIsNotSimilar   false iff the next job will be similar
         */
        public void cancel( boolean nextIsNotSimilar ) {

            /* If the next job is quite like the old plot (e.g. pan or zoom)
             * it's a good idea to let the old one complete, so that
             * intermediate views are displayed rather than the screen
             * going blank until there are no more plots pending.
             * Pans and zooms typically come in a cascade of similar jobs.
             * If a subsample plot is happening, only let that one complete
             * and not the full plot, so that the screen refresh happens
             * reasonably quickly. 
             * If the plot is different (different layers or data) then
             * cancel the existing plot immediately and start work on
             * a new one. */
            boolean mayInterruptIfRunning = nextIsNotSimilar;
            if ( stepCanceler_ != null ) {
                fullCanceler_.cancel( true );
                stepCanceler_.cancel( mayInterruptIfRunning );
            }
            else if ( fullCanceler_ != null ) {
                fullCanceler_.cancel( mayInterruptIfRunning );
            }
        }

        /**
         * Accepts a workings object calculated for this job and applies
         * it to the parent panel.
         * May be called from any thread.
         *
         * @param  workings  workings object, may be null
         */
        private void submitWorkings( final Workings<A> workings ) {

            /* A null result may mean that the plot was interrupted or
             * that the result was the same as for the previously
             * calculated plot.  Either way, keep the same output
             * graphics as before.  If the return is non-null,
             * repaint it. */
            if ( workings != null ) {
                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        boolean plotChange =
                            ! workings.getDataIconIdList()
                             .equals( workings_.getDataIconIdList() );
                        workings_ = workings;
                        for ( Workings.ZoneWork zone : workings.zones_ ) {
                            zone.updateAxisController();
                        }
                        repaint();

                        /* If the plot changed materially, notify listeners. */
                        if ( plotChange ) {
                            fireChangeEvent();
                        }
                    }
                } );
            }
        }

        /**
         * Indicates whether the plot job which this object will execute
         * is similar to a given plot job, for the purposes of working out
         * whether to let an old job complete.
         *
         * @param  plotJob   other plot job
         * @return  true iff this object's job is like the other one
         */
        public boolean isSimilar( PlotJob otherJob ) {
            return simObj_.equals( getSimilarityObject( otherJob ) );
        }
    }

    /**
     * Icon that can be used for exporting the current plot to an
     * external graphics format.
     */
    private static class ExportIcon implements Icon {

        private final Workings workings_;
        private final boolean forceBitmap_;
        private final PaperTypeSelector ptSel_;
        private final Compositor compositor_;

        /** 
         * Constructor.
         *
         * @param  workings  contains plot state
         * @param  forceBitmap   true to force bitmap output of vector graphics,
         *                       false to use default behaviour
         * @param  ptSel   rendering policy
         * @param  compositor  compositor for composition of transparent pixels
         */
        public ExportIcon( Workings workings, boolean forceBitmap,
                           PaperTypeSelector ptSel, Compositor compositor ) {
            workings_ = workings;
            forceBitmap_ = forceBitmap;
            ptSel_ = ptSel;
            compositor_ = compositor;
        }

        public int getIconWidth() {
            return workings_.extBounds_.width;
        }

        public int getIconHeight() {
            return workings_.extBounds_.height;
        }

        public void paintIcon( Component c, Graphics g, int x, int y ) {
            g.translate( x, y );
            Shape clip = g.getClip();
            DataStore dataStore = workings_.dataStore_;
            boolean cached = false;
            Collection<Object> plans = null;
            for ( Workings.ZoneWork zone : workings_.zones_ ) {
                PlotPlacement placer = zone.placer_;
                PlotLayer[] layers = zone.layers_;
                Map<AuxScale,Range> auxRanges = zone.auxClipRangeMap_;
                LayerOpt[] opts = PaperTypeSelector.getOpts( layers );
                PaperType paperType =
                      forceBitmap_
                    ? ptSel_.getPixelPaperType( opts, compositor_, c )
                    : ptSel_.getVectorPaperType( opts );
                if ( clip != null &&
                     ! clip.intersects( placer.getSurface()
                                              .getPlotBounds() ) ) {
                    layers = new PlotLayer[ 0 ];
                }
                PlotUtil.createPlotIcon( placer, layers, auxRanges,
                                         dataStore, paperType, cached, plans )
                        .paintIcon( c, g, 0, 0 );
            }
            g.translate( -x, -y );
        }
    }

    /**
     * Icon used for point highlighting.
     */
    private static class HighlightIcon implements Icon {
        private final int size_;
        private final int size2_;
        private final Stroke stroke_ =
            new BasicStroke( 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND );
        private final Map<RenderingHints.Key,Object> hints_;
        private final Color color1_ = new Color( 0xffffff );
        private final Color color2_ = new Color( 0x000000 );
        HighlightIcon() {
            size_ = 6;
            size2_ = size_ * 2 + 1;
            hints_ = new HashMap<RenderingHints.Key,Object>();
            hints_.put( RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY );
        }
        public int getIconWidth() {
            return size2_;
        }
        public int getIconHeight() {
            return size2_;
        }
        public void paintIcon( Component c, Graphics g, int x, int y ) {
            Graphics2D g2 = (Graphics2D) g;
            Stroke stroke0 = g2.getStroke();
            Color color0 = g2.getColor();
            RenderingHints hints0 = g2.getRenderingHints();
            g2.setRenderingHints( hints_ );
            g2.setStroke( stroke_ );
            int xoff = x + size_;
            int yoff = y + size_;
            g2.translate( xoff, yoff );
            g2.setColor( color1_ );
            drawTarget( g2, size_ - 1 );
            g2.setColor( color2_ );
            drawTarget( g2, size_ );
            g2.translate( -xoff, -yoff );
            g2.setColor( color0 );
            g2.setStroke( stroke0 );
            g2.setRenderingHints( hints0 );
        }
        private static void drawTarget( Graphics g, int size ) {
            int size2 = size * 2 + 1;
            int s = size - 2;
            int s2 = s * 2;
            g.drawOval( -size, -size, size2, size2 );
            g.drawLine( 0, +s, 0, +s2 );
            g.drawLine( 0, -s, 0, -s2 );
            g.drawLine( +s, 0, +s2, 0 );
            g.drawLine( -s, 0, -s2, 0 );
        }
    }
}
