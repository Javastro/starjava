package uk.ac.starlink.topcat.plot2;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import uk.ac.starlink.ttools.plot.Style;
import uk.ac.starlink.ttools.plot2.DataGeom;
import uk.ac.starlink.ttools.plot2.LegendEntry;
import uk.ac.starlink.ttools.plot2.PlotLayer;
import uk.ac.starlink.ttools.plot2.PlotUtil;
import uk.ac.starlink.ttools.plot2.ReportMap;
import uk.ac.starlink.ttools.plot2.config.ConfigKey;
import uk.ac.starlink.ttools.plot2.config.ConfigMap;
import uk.ac.starlink.ttools.plot2.config.Specifier;
import uk.ac.starlink.ttools.plot2.config.StyleKeys;
import uk.ac.starlink.ttools.plot2.data.DataSpec;
import uk.ac.starlink.topcat.AlignedBox;
import uk.ac.starlink.topcat.RowSubset;
import uk.ac.starlink.topcat.TablesListComboBox;
import uk.ac.starlink.topcat.TopcatEvent;
import uk.ac.starlink.topcat.TopcatListener;
import uk.ac.starlink.topcat.TopcatModel;
import uk.ac.starlink.topcat.TypedListModel;

/**
 * Plot layer control which manages coordinates and subsets in a common way
 * for multiple layers defined by one or more forms.
 * It provides a tab for common coordinates (including table)
 * and a tab for configuring subset-specific defaults.
 * Concrete subclasses must provide their form panels.
 *
 * @author   Mark Taylor
 * @since    8 Jan 2014
 */
public abstract class FormLayerControl
        extends TabberControl implements LayerControl {

    private final PositionCoordPanel posCoordPanel_;
    private final boolean autoPopulate_;
    private final TablesListComboBox tableSelector_;
    private final WrapperListModel subListModel_;
    private final SubsetConfigManager subsetManager_;
    private final TopcatListener tcListener_;
    private final SubsetStack subStack_;
    private final Specifier<ZoneId> zsel_;
    private TopcatModel tcModel_;

    /**
     * Constructor.
     *
     * @param  posCoordPanel  panel for entering table and basic positional
     *                        coordinates
     * @param  tablesModel   list of available tables
     * @param  zsel    zone id specifier, may be null for single-zone plots
     * @param  autoPopulate  if true, when the table is changed an attempt
     *                       will be made to initialise the coordinate fields
     *                       with some suitable values
     * @param  nextSupplier  manages global dispensing for some style options
     * @param  tcListener  listener for TopcatEvents; this manager will arrange
     *                     for it to listen to whatever is the currently
     *                     selected TopcatModel
     * @param  controlIcon  icon for control stack
     */
    protected FormLayerControl( PositionCoordPanel posCoordPanel,
                                TypedListModel<TopcatModel> tablesModel,
                                Specifier<ZoneId> zsel, boolean autoPopulate,
                                NextSupplier nextSupplier,
                                TopcatListener tcListener, Icon controlIcon ) {
        super( null, controlIcon );
        posCoordPanel_ = posCoordPanel;
        zsel_ = zsel;
        autoPopulate_ = autoPopulate;
        final TopcatListener externalTcListener = tcListener;

        /* Set up a selector for which table to plot. */
        tableSelector_ = new TablesListComboBox( tablesModel, 250 );

        /* Ensure listeners are notified when the table selection changes,
         * or when anything else about this control changes. */
        final ActionListener forwarder = getActionForwarder();
        posCoordPanel_.addActionListener( forwarder );
        tableSelector_.addItemListener( new ItemListener() {
            public void itemStateChanged( ItemEvent evt ) {
                tableChanged();
                forwarder.actionPerformed( new ActionEvent( this, 0,
                                                            "Table" ) );
            }
        } );

        /* Set up the panel for selecting the table and position coordinates. */
        JComponent posbox = AlignedBox.createVerticalBox();
        JComponent tline = Box.createHorizontalBox();
        tline.add( new JLabel( "Table: " ) );
        tline.add( tableSelector_ );
        JComponent coordComp = posCoordPanel_.getComponent();
        posbox.add( tline );
        posbox.add( Box.createVerticalStrut( 5 ) );
        posbox.add( coordComp );

        /* Set up a manager for per-subset configuration. */
        subsetManager_ =
            new SubsetConfigManager( nextSupplier,
                                     new ConfigKey[] { StyleKeys.LABEL,
                                                       StyleKeys.SHOW_LABEL } );
        subsetManager_.addActionListener( forwarder );

        /* By using a wrapper list here and resetting its base model when
         * the table changes, other components in this control can just
         * listen for changes to the wrapper model, they don't have to be
         * messaged explicitly when the underlying model changes. */
        subListModel_ = new WrapperListModel();
        subStack_ = new SubsetStack( subListModel_, subsetManager_ );
        subStack_.addActionListener( forwarder );

        /* Required to make the controlLabel display sensitive to changes in
         * the topcat model label. */
        tcListener_ = new TopcatListener() {
            public void modelChanged( TopcatEvent evt ) {
                handleTopcatEvent( evt );
                externalTcListener.modelChanged( evt );
            }
        };

        /* Position the components in tabs of this control. */
        addControlTab( "Position", posbox, true );
        addControlTab( "Subsets", subStack_.getComponent(), false );
    }

    @Override
    public String getControlLabel() {
        return tcModel_ == null ? "<no table>" : tcModel_.toString();
    }

    /**
     * Returns the panel in which positional coordinates are entered.
     *
     * @return  positional coordinate panel
     */
    public PositionCoordPanel getPositionCoordPanel() {
        return posCoordPanel_;
    }

    /**
     * Returns this control's per-subset configuration manager.
     *
     * @return   subset manager
     */
    public SubsetConfigManager getSubsetManager() {
        return subsetManager_;
    }

    /**
     * Returns this control's selectable stack of subsets.
     *
     * @return  subset stack
     */
    public SubsetStack getSubsetStack() {
        return subStack_;
    }

    /**
     * Returns a list of all the form controls, active or not, currently
     * managd by this layer control.
     *
     * @return  list of all form controls
     */
    protected abstract FormControl[] getFormControls();

    /**
     * Indicates whether a given form control is contributing the the plot
     * on behalf of this layer control.
     *
     * @param  fc  form control managed by this layer control
     * @return  true iff fc is active (contributing to plot)
     */
    protected abstract boolean isControlActive( FormControl fc );

    public TopcatLayer[] getLayers() {
        RowSubset[] subsets = subStack_.getSelectedSubsets();
        GuiCoordContent[] posContents = posCoordPanel_.getContents();
        if ( tcModel_ == null || posContents == null || subsets == null ) {
            return new TopcatLayer[ 0 ];
        }
        DataGeom geom = posCoordPanel_.getDataGeom();
        ConfigMap coordConfig = posCoordPanel_.getConfig();
        FormControl[] fcs = getActiveFormControls();
        List<TopcatLayer> layerList = new ArrayList<TopcatLayer>();
        for ( int is = 0; is < subsets.length; is++ ) {
            RowSubset subset = subsets[ is ];
            String leglabel = getLegendLabel( subset );
            for ( int ifc = 0; ifc < fcs.length; ifc++ ) {
                FormControl fc = fcs[ ifc ];
                GuiCoordContent[] extraContents = fc.getExtraCoordContents();
                if ( extraContents != null ) {
                    GuiCoordContent[] contents =
                        PlotUtil.arrayConcat( posContents, extraContents );
                    DataSpec dspec =
                        new GuiDataSpec( tcModel_, subset, contents );
                    PlotLayer plotLayer = fc.createLayer( geom, dspec, subset );
                    if ( plotLayer != null ) {
                        ConfigMap config = new ConfigMap();
                        config.putAll( coordConfig );
                        config.putAll( fc.getExtraConfig() );
                        config.putAll( fc.getStylePanel().getConfig( subset ) );
                        TopcatLayer layer =
                            new TopcatLayer( plotLayer, config, leglabel,
                                             tcModel_, contents, subset );
                        layerList.add( layer );
                    }
                }
            }
        }
        return layerList.toArray( new TopcatLayer[ 0 ] );
    }

    public LegendEntry[] getLegendEntries() {
        if ( tcModel_ == null ) {
            return new LegendEntry[ 0 ];
        }
        Map<RowSubset,List<Style>> rsetStyles =
            getStylesBySubset( getLayers() );
        List<LegendEntry> entries = new ArrayList<LegendEntry>();
        for ( RowSubset rset : rsetStyles.keySet() ) {
            Style[] styles = rsetStyles.get( rset ).toArray( new Style[ 0 ] );
            String label = getLegendLabel( rset );
            if ( label != null ) {
                LegendEntry entry = new LegendEntry( label, styles );
                assert entry.equals( new LegendEntry( label, styles ) );
                entries.add( entry );
            }
        }
        return entries.toArray( new LegendEntry[ 0 ] );
    }

    /**
     * Returns the label to use in the legend for a given row subset
     * controlled by this control.  Null means the item should not appear
     * in the legend.
     *
     * @param  rset  row subset
     * @return  legend label, or null if absent from legend
     */
    private String getLegendLabel( RowSubset rset ) {
        if ( rset == null ) {
            return null;
        }
        ConfigMap config = subsetManager_.getConfigger( rset ).getConfig();
        boolean show = config.get( StyleKeys.SHOW_LABEL );
        if ( show ) {
            String label = config.get( StyleKeys.LABEL );
            return label == null || label.trim().length() == 0
                 ? tcModel_.getID() + ": " + rset.getName()
                 : label;
        }
        else {
            return null;
        }
    }

    public Specifier<ZoneId> getZoneSpecifier() {
        return zsel_;
    }

    public TablesListComboBox getTableSelector() {
        return tableSelector_;
    }

    public void submitReports( Map<LayerId,ReportMap> reports ) {
        RowSubset[] subsets = subStack_.getSelectedSubsets();
        GuiCoordContent[] posContents = posCoordPanel_.getContents();
        if ( tcModel_ == null || posContents == null || subsets == null ) {
            return;
        }
        DataGeom geom = posCoordPanel_.getDataGeom();
        for ( FormControl fc : getFormControls() ) {
            Map<RowSubset,ReportMap> sreports =
                new LinkedHashMap<RowSubset,ReportMap>();
            if ( tcModel_ != null && posContents != null && subsets != null &&
                 isControlActive( fc ) ) {
                GuiCoordContent[] extraContents = fc.getExtraCoordContents();
                if ( extraContents != null ) {
                    GuiCoordContent[] contents =
                        PlotUtil.arrayConcat( posContents, extraContents );
                    for ( RowSubset rset : subsets ) {
                        DataSpec dspec =
                            new GuiDataSpec( tcModel_, rset, contents );
                        PlotLayer layer = fc.createLayer( geom, dspec, rset );
                        if ( layer != null ) {
                            ReportMap report =
                                reports.get( LayerId.createLayerId( layer ) );
                            if ( report != null ) {
                                sreports.put( rset, report );
                            }
                        }
                    }
                }
            }
            fc.submitReports( sreports );
        }
    }

    /**
     * Returns the controls in the form control list which are contributing
     * to the plot.  Controls that the user has deactivated (unchecked)
     * are ignored.
     *
     * @return  list of active form controls
     */
    private FormControl[] getActiveFormControls() {
        List<FormControl> fcs = new ArrayList<FormControl>();
        for ( FormControl fc : getFormControls() ) {
            if ( isControlActive( fc ) ) {
                fcs.add( fc );
            }
        }
        return fcs.toArray( new FormControl[ 0 ] );
    }

    /**
     * Invoked when an event is received for the currently selected
     * TopcatModel.
     */
    private void handleTopcatEvent( TopcatEvent evt ) {
        int code = evt.getCode();

        /* Label change event: forward to the plot listener so it can
         * replot with a new legend label etc. */
        if ( code == TopcatEvent.LABEL ) {
            getActionForwarder()
           .actionPerformed( new ActionEvent( this, 0, "label" ) );
        }

        /* Subset show event: ensure that the subset is selected in this plot
         * and forward to the plot listener so it can replot if required. */
        else if ( code == TopcatEvent.SHOW_SUBSET ) {
            RowSubset rset = (RowSubset) evt.getDatum();
            subStack_.setSelected( rset, true );
            getActionForwarder()
           .actionPerformed( new ActionEvent( this, 0, "subset" ) );
        }
    }

    /**
     * Returns all those style objects associated with a given RowSubset
     * for a given list of plot layers.
     *
     * @param    tcLayers   layers
     * @return   ordered RowSubset->Styles map
     */
    private static Map<RowSubset,List<Style>>
                   getStylesBySubset( TopcatLayer[] tcLayers ) {
        Map<RowSubset,List<Style>> map =
            new LinkedHashMap<RowSubset,List<Style>>();
        for ( TopcatLayer tcLayer : tcLayers ) {
            PlotLayer layer = tcLayer.getPlotLayer();
            DataSpec dspec = layer.getDataSpec();
            if ( dspec != null ) {
                Object mask = dspec.getMaskId();
                assert mask instanceof RowSubset;
                if ( mask instanceof RowSubset ) {
                    RowSubset rset = (RowSubset) mask;
                    if ( ! map.containsKey( rset ) ) {
                        map.put( rset, new ArrayList<Style>() );
                    }
                    map.get( rset ).add( layer.getStyle() );
                }
            }
        }
        return map;
    }

    public String getCoordLabel( String userCoordName ) {
        String label = GuiCoordContent
                      .getCoordLabel( userCoordName,
                                      posCoordPanel_.getContents() );
        FormControl[] fcs = getActiveFormControls();
        for ( int ifc = 0; ifc < fcs.length && label == null; ifc++ ) {
            label = GuiCoordContent
                   .getCoordLabel( userCoordName,
                                   fcs[ ifc ].getExtraCoordContents() );
        }
        return label;
    }

    /**
     * Sets in the GUI the topcat model for which this control
     * is making plots.
     *
     * @param  tcModel  new topcat model
     */
    public void setTopcatModel( TopcatModel tcModel ) {
        tableSelector_.setSelectedItem( tcModel );
    }

    /**
     * Returns the table for which this control is currently making plots.
     *
     * @return  topcat model
     */
    public TopcatModel getTopcatModel() {
        return (TopcatModel) tableSelector_.getSelectedItem();
    }

    /**
     * Called when the TopcatModel for which this control is generating plots
     * is changed.  Usually this will be because the user has selected
     * a new one from the table selector.
     */
    private void tableChanged() {

        /* Reassign the listener to listen to the new current model
         * not the old one. */
        if ( tcModel_ != null ) {
            tcModel_.removeTopcatListener( tcListener_ );
        }
        tcModel_ = (TopcatModel) tableSelector_.getSelectedItem();
        if ( tcModel_ != null ) {
            tcModel_.addTopcatListener( tcListener_ );
        }

        /* Message the position selection panel and form controls so they
         * can update their lists of selectable columns etc. */
        posCoordPanel_.setTable( tcModel_, autoPopulate_ );
        FormControl[] fcs = getActiveFormControls();
        for ( int ifc = 0; ifc < fcs.length; ifc++ ) {
            fcs[ ifc ].setTable( tcModel_, subsetManager_, subStack_ );
        }

        /* If there is no new table, just clear the list of subsets
         * and the set of selected subsets. */
        if ( tcModel_ == null ) {
            subStack_.setSelectedSubsets( new RowSubset[ 0 ] );
            subListModel_.setBaseModel( new DefaultListModel() );
        }

        /* Otherwise, configure the subset list to be as much like the
         * old one as is reasonable.  Just leaving it the same won't work
         * very well, since the RowSubset objects will (apart from ALL)
         * be different between the old and new table, but if they have
         * the same names we can try to copy the configuration. */
        else {

            /* Store some things we need to know about the old configuration. */
            Set<String> oldSelectedSubsetNames = new HashSet<String>();
            RowSubset[] oldSelSubsets = subStack_.getSelectedSubsets();
            for ( int i = 0; i < oldSelSubsets.length; i++ ) {
                oldSelectedSubsetNames.add( oldSelSubsets[ i ].getName() );
            }
            Set<RowSubset> oldSubsets = new HashSet<RowSubset>();
            for ( int i = 0; i < subListModel_.getSize(); i++ ) {
                oldSubsets.add( (RowSubset) subListModel_.getElementAt( i ) );
            }

            /* Clear the selections while we do some manipulations so that
             * no plots will result. */
            subStack_.setSelectedSubsets( new RowSubset[ 0 ] );

            /* Set up the subset stack with the subsets for the new table. */
            subListModel_.setBaseModel( tcModel_.getSubsets() );

            /* Work out if there are any subsets in the new table with
             * names matching those selected in the old table. */
            List<RowSubset> newSubsets = new ArrayList<RowSubset>();
            for ( RowSubset rset : tcModel_.getSubsets() ) {
                if ( oldSelectedSubsetNames.contains( rset.getName() ) ) {
                    newSubsets.add( rset );
                }
            }

            /* Decide which subsets will be selected for the new table.
             * If at least one has the same name as one selected in the old
             * table, use the same set of selected subset names as before.
             * If there are none, or if there is exactly one and it's ALL,
             * use just the new table's currently selected subset. */
            RowSubset[] selSubsets =
                  newSubsets.isEmpty() ||
                  newSubsets.size() == 1 && newSubsets.get( 0 ) == RowSubset.ALL
                ? new RowSubset[] { tcModel_.getSelectedSubset() }
                : newSubsets.toArray( new RowSubset[ 0 ] );

            /* Now set up the subset configuration manager so that any
             * entries for subsets in the old table are transferred to
             * entries for subsets in the new table with similar names. */
            Map<String,Configger> subconMap = new HashMap<String,Configger>();
            for ( RowSubset rset : oldSubsets ) {
                if ( subsetManager_.hasConfigger( rset ) ) {
                    subconMap.put( rset.getName(),
                                   subsetManager_.getConfigger( rset ) );
                }
            }
            for ( RowSubset rset : tcModel_.getSubsets() ) {
                Configger configger = subconMap.get( rset.getName() );
                if ( configger != null ) {
                    subsetManager_.setConfig( rset, configger.getConfig() );
                }
            }

            /* Initialise the selected subsets. */ 
            subStack_.setSelectedSubsets( selSubsets );
        }
    }

    /**
     * List model which serves as a wrapper for some base list model,
     * but the base model can be changed.  If that happens, an appropriate
     * ContentsChanged event is sent to listeners of this model.
     * This allows external listeners just to listen to this model,
     * and not to have to worry about keeping changing the target of their
     * listeners when state of the layer control changes.
     */
    private static class WrapperListModel implements ListModel {
        private final List<ListDataListener> listenerList_;
        private final ListDataListener listDataForwarder_;
        private ListModel baseModel_;

        /**
         * Constructor.
         */
        WrapperListModel() {
            listenerList_ = new ArrayList<ListDataListener>();
            listDataForwarder_ = new ListDataListener() {
                public void contentsChanged( ListDataEvent evt ) {
                    for ( ListDataListener l : listenerList_ ) {
                        l.contentsChanged( evt );
                    }
                }
                public void intervalAdded( ListDataEvent evt ) {
                    for ( ListDataListener l : listenerList_ ) {
                        l.intervalAdded( evt );
                    }
                }
                public void intervalRemoved( ListDataEvent evt ) {
                    for ( ListDataListener l : listenerList_ ) {
                        l.intervalRemoved( evt );
                    }
                }
            };
        }

        /**
         * Sets the model whose data will back this model.
         *
         * @param  baseModel  base list model
         */
        public void setBaseModel( ListModel baseModel ) {
            int imax = 0;
            if ( baseModel_ != null ) {
                baseModel_.removeListDataListener( listDataForwarder_ );
                imax = Math.max( imax, baseModel_.getSize() );
            }
            baseModel_ = baseModel;
            if ( baseModel_ != null ) {
                baseModel_.addListDataListener( listDataForwarder_ );
                imax = Math.max( imax, baseModel_.getSize() );
            }
            ListDataEvent evt =
                new ListDataEvent( this, ListDataEvent.CONTENTS_CHANGED,
                                   0, imax );
            for ( ListDataListener l : listenerList_ ) {
                l.contentsChanged( evt );
            }
        } 

        public Object getElementAt( int index ) {
            return baseModel_ == null ? null : baseModel_.getElementAt( index );
        }

        public int getSize() {
            return baseModel_ == null ? 0 : baseModel_.getSize();
        }

        public void addListDataListener( ListDataListener listener ) {
            listenerList_.add( listener );
        }

        public void removeListDataListener( ListDataListener listener ) {
            listenerList_.remove( listener );
        }
    }
}
