package uk.ac.starlink.topcat.plot2;

import gnu.jel.CompilationException;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.Icon;
import uk.ac.starlink.table.ColumnData;
import uk.ac.starlink.table.ValueInfo;
import uk.ac.starlink.topcat.BasicAction;
import uk.ac.starlink.topcat.ColumnDataComboBoxModel;
import uk.ac.starlink.topcat.ResourceIcon;
import uk.ac.starlink.topcat.RowSubset;
import uk.ac.starlink.topcat.TopcatListener;
import uk.ac.starlink.topcat.TopcatModel;
import uk.ac.starlink.ttools.plot.Styles;
import uk.ac.starlink.ttools.plot2.PlotType;
import uk.ac.starlink.ttools.plot2.PlotUtil;
import uk.ac.starlink.ttools.plot2.Plotter;
import uk.ac.starlink.ttools.plot2.config.ConfigKey;
import uk.ac.starlink.ttools.plot2.config.ConfigMap;
import uk.ac.starlink.ttools.plot2.config.StyleKeys;
import uk.ac.starlink.ttools.plot2.data.Coord;

/**
 * Control manager that uses GangLayerControls to provide
 * panels that allow you to enter the position values once
 * for a given table and then go to other tabs in the control
 * to customise the layers generated.
 * 
 * @author   Mark Taylor
 * @since    15 Mar 2013
 */
public class GangControlManager implements ControlManager {

    private final ControlStack stack_;
    private final PlotType plotType_;
    private final PlotTypeGui plotTypeGui_;
    private final Configger baseConfigger_;
    private final TopcatListener tcListener_;
    private final NextSupplier nextSupplier_;
    private final SortedMap<Integer,List<Plotter>> plotterMap_;
    private final Action[] stackActs_;

    private static final Logger logger_ =
        Logger.getLogger( "uk.ac.starlink.topcat.plot2" );

    /**
     * Constructor.
     */
    public GangControlManager( ControlStack stack, PlotType plotType,
                               PlotTypeGui plotTypeGui, Configger baseConfigger,
                               TopcatListener tcListener ) {
        stack_ = stack;
        plotType_ = plotType;
        plotTypeGui_ = plotTypeGui;
        baseConfigger_ = baseConfigger;
        tcListener_ = tcListener;
        nextSupplier_ = new NextSupplier();
        nextSupplier_.putValues( StyleKeys.COLOR, Styles.COLORS );
        List<Action> stackActList = new ArrayList<Action>();

        /* Split the list up by the number of positional coordinates
         * they have. */
        plotterMap_ = new TreeMap<Integer,List<Plotter>>();
        plotterMap_.put( 0, new ArrayList<Plotter>() );
        plotterMap_.put( 1, new ArrayList<Plotter>() );
        plotterMap_.put( 2, new ArrayList<Plotter>() );
        Plotter[] plotters = plotType_.getPlotters();
        for ( int i = 0; i < plotters.length; i++ ) {
            Plotter plotter = plotters[ i ];
            int npos = plotter.getPositionCount();
            if ( ! plotterMap_.containsKey( npos ) ) {
                plotterMap_.put( npos, new ArrayList<Plotter>() );
            }
            plotterMap_.get( npos ).add( plotter );
        }

        /* Add an action for single-position plotters. */
        final Icon icon1 = ResourceIcon.PLOT_DATA;
        stackActList.add( new BasicAction( "Add Position Plot", icon1,
                                           "Add a new positional "
                                         + "plot control to the stack" ) {
            public void actionPerformed( ActionEvent evt ) {
                stack_.addControl( createGangControl( 1, icon1, true ) );
            }
        } );

        /* Add an action for double-position plotters. */
        final Icon icon2 = ResourceIcon.PLOT_PAIR;
        stackActList.add( new BasicAction( "Add Pair Plot", icon2,
                                           "Add a new pair position "
                                         + "plot control to the stack" ) {
            public void actionPerformed( ActionEvent evt ) {
                stack_.addControl( createGangControl( 2, icon2, true ) );
            }
        } );

        /* Add actions for non-positional plotters. */
        for ( Plotter plotter : plotterMap_.get( 0 ) ) {
            Action stackAct = PlotterStackAction.createAction( plotter, stack );
            if ( stackAct != null ) {
                stackActList.add( stackAct );
            }
            else {
                logger_.warning( "No GUI available for plotter "
                               + plotter.getPlotterName() );
            }
        }

        /* For now, we don't take steps to present triple-positional plotters
         * and beyond, because there aren't any.  But warn if some arise. */
        stackActs_ = stackActList.toArray( new Action[ 0 ] );
        int unused = plotterMap_.tailMap( new Integer( 3 ) ).size();
        if ( unused > 0 ) {
            logger_.warning( unused + " plotters not presented in GUI" );
        }
    }

    public Action[] getStackActions() {
        return stackActs_;
    }

    public Control createDefaultControl( TopcatModel tcModel ) {
        GangLayerControl control =
            createGangControl( 1, ResourceIcon.PLOT_DATA, true );
        control.setTopcatModel( tcModel );
        return control;
    }

    public void addLayer( LayerCommand lcmd ) throws LayerException {
        logger_.info( "Add layer: " + lcmd );
        getGangControl( lcmd ).addLayer( lcmd );
    }

    /**
     * Returns a control to which a specified layer can be added.
     * If a suitable control is currently in the stack, that will be returned.
     * Otherwise, a new control will be constructed and placed into the
     * stack.
     *
     * @param   lcmd  specifies a layer that wants to be added
     * @return  control in the stack for which <code>addLayer(lcmd)</code>
     *          will work
     */
    private GangLayerControl getGangControl( LayerCommand lcmd )
            throws LayerException {
        ControlStackModel stackModel = stack_.getStackModel();

        /* Try to find and return an existing compatible control. */
        for ( int ic = 0; ic < stackModel.getSize(); ic++ ) {
            Control control = stackModel.getControlAt( ic );
            if ( control instanceof GangLayerControl ) {
                GangLayerControl gangControl = (GangLayerControl) control;
                if ( isCompatible( gangControl, lcmd ) ) {
                    return gangControl;
                }
            }
        }

        /* If there wasn't one, create a new one, add it to the stack,
         * and return it. */
        GangLayerControl control = createGangControl( lcmd );
        stack_.addControl( control );
        return control;
    }

    /**
     * Determines whether a given control can have a specified layer
     * added to it.
     *
     * @param  control  existing control
     * @param  lcmd   specifies layer to add
     * @return  true iff <code>control.addLayer(lcmd)</code> will work
     */
    private static boolean isCompatible( GangLayerControl control,
                                         LayerCommand lcmd ) {

        /* Note the implementation of this method is closely tied to the
         * implementation of GangLayerControl.addLayer. */

        /* Must have the same table data. */
        if ( lcmd.getTopcatModel() != control.getTopcatModel() ) {
            return false;
        }

        /* Must have the same positional coordinates.
         * This test currently requires all the coordinates to be the same,
         * I think that could be relaxed to just the positional ones. */
        if ( ! lcmd.getCoordValues()
                   .equals( GuiCoordContent
                           .getCoordValues( control.getPositionCoordPanel()
                                                   .getContents() ) ) ) {
            return false;
        }

        /* Must have the same, single, subset.  It could be possible
         * under some circumstances to add to a gang control with
         * multiple subsets, but the logic is tricky, so this is not
         * currently supported by addLayer. */
        RowSubset rset = lcmd.getRowSubset();
        SubsetStack subStack = control.getSubsetStack();
        if ( ! Arrays.equals( subStack.getSelectedSubsets(),
                              new RowSubset[] { rset } ) ) {
            return false;
        }

        /* Any config options specified for the new layer must not conflict
         * with config options (probably colour) currently in force for
         * the new layer's subset. */
        SubsetConfigManager subManager = control.getSubsetManager();
        if ( subManager.hasConfigger( rset ) ) {
            ConfigMap ctrlConfig = subManager.getConfigger( rset ).getConfig();
            ConfigMap cmdConfig = lcmd.getConfig();
            for ( ConfigKey<?> key : cmdConfig.keySet() ) {
                if ( ctrlConfig.keySet().contains( key ) &&
                     ! PlotUtil.equals( ctrlConfig.get( key ),
                                        cmdConfig.get( key ) ) ) {
                    return false;
                }
            }
        }

        /* If it passes all those tests, it's compatible. */
        return true;
    }

    /**
     * Constructs a new gang layer control which is capable of having a
     * specified layer added to it.
     *
     * @param  lcmd  layer specification
     * @return  new control for which <code>addLayer(lcmd)</code> will work
     */
    private GangLayerControl createGangControl( LayerCommand lcmd )
            throws LayerException {

        /* Create the control. */
        int npos = lcmd.getPlotter().getPositionCount();
        Icon icon = npos == 1 ? ResourceIcon.PLOT_DATA : ResourceIcon.PLOT_PAIR;
        GangLayerControl control = createGangControl( npos, icon, false );

        /* Set the table. */
        control.setTopcatModel( lcmd.getTopcatModel() );

        /* Set up the positional coordinates. */
        PositionCoordPanel posCoordPanel = control.getPositionCoordPanel();
        Coord[] posCoords = posCoordPanel.getCoords();
        Map<String,String> coordValues = lcmd.getCoordValues();
        for ( int ic = 0; ic < posCoords.length; ic++ ) {
            ValueInfo[] infos = posCoords[ ic ].getUserInfos();
            for ( int iu = 0; iu < infos.length; iu++ ) {
                String name = infos[ iu ].getName();
                String value = coordValues.get( name );
                if ( value != null ) {
                    ColumnDataComboBoxModel colModel =
                        posCoordPanel.getColumnSelector( ic, iu );
                    ColumnData colData;
                    try {
                        colData = colModel.stringToColumnData( value );
                    }
                    catch ( CompilationException e ) {
                        throw new LayerException( "Can't compile: " + value,
                                                  e );
                    }
                    posCoordPanel.getColumnSelector( ic, iu )
                                 .setSelectedItem( colData );
                }
            }
        }

        /* Set up per-subset configuration. */
        control.getSubsetManager()
               .setConfig( lcmd.getRowSubset(), lcmd.getConfig() );

        /* Return. */
        return control;
    }

    /**
     * Creates a new empty gang layer control.
     *
     * @param   npos  number of groups of positional coordinates for entry
     * @return   gang control, or null if it would be useless
     */
    private GangLayerControl createGangControl( int npos, Icon icon,
                                                boolean autoPlot ) {
        List<Plotter> plotterList = plotterMap_.get( npos );
        if ( plotterList != null && plotterList.size() > 0 ) {
            PositionCoordPanel coordPanel =
                plotTypeGui_.createPositionCoordPanel( npos );
            boolean autoPop = npos == 1;
            GangLayerControl control = 
                new GangLayerControl( coordPanel, autoPop,
                                      plotterList.toArray( new Plotter[ 0 ] ),
                                      baseConfigger_, nextSupplier_,
                                      tcListener_, icon );
            if ( autoPlot ) {
                control.addDefaultLayer();
            }
            return control;
        }
        else {
            return null;
        }
    }
}
