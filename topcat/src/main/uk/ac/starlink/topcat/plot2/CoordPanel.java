package uk.ac.starlink.topcat.plot2;

import gnu.jel.CompilationException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import uk.ac.starlink.table.ColumnData;
import uk.ac.starlink.table.ColumnInfo;
import uk.ac.starlink.table.ConstantColumn;
import uk.ac.starlink.table.ValueInfo;
import uk.ac.starlink.table.gui.LabelledComponentStack;
import uk.ac.starlink.topcat.ActionForwarder;
import uk.ac.starlink.topcat.ColumnDataComboBoxModel;
import uk.ac.starlink.topcat.LineBox;
import uk.ac.starlink.topcat.TopcatModel;
import uk.ac.starlink.ttools.plot2.PlotUtil;
import uk.ac.starlink.ttools.plot2.config.ConfigKey;
import uk.ac.starlink.ttools.plot2.config.ConfigMap;
import uk.ac.starlink.ttools.plot2.data.Coord;
import uk.ac.starlink.ttools.plot2.data.Input;
import uk.ac.starlink.ttools.plot2.data.InputMeta;
import uk.ac.starlink.util.gui.ComboBoxBumper;

/**
 * GUI component for entry of Coord values as table column expressions.
 *
 * @author   Mark Taylor
 * @since    13 Mar 2013
 */
public class CoordPanel {

    private final Coord[] coords_;
    private final ConfigSpecifier cspec_;
    private final ActionForwarder forwarder_;
    private final JComboBox[][] colSelectors_;
    private final JComponent panel_;   
    private TopcatModel tcModel_;

    /**
     * Constructs a CoordPanel for selecting just Coords.
     *
     * @param  coords  coordinate definitions for which values are required
     */
    public CoordPanel( Coord[] coords ) {
        this( coords, new ConfigKey[ 0 ] );
    }

    /**
     * Constructs a CoordPanel for selecting Coords and Config values.
     *
     * @param  coords  coordinate definitions for which values are required
     * @param  configKeys   config value keys
     */
    public CoordPanel( Coord[] coords, ConfigKey[] configKeys ) {
        panel_ = new JPanel( new BorderLayout() );
        coords_ = coords;
        forwarder_ = new ActionForwarder();
        JComponent box = Box.createVerticalBox();

        /* Set up config specifiers for the given config keys. */
        cspec_ = new ConfigSpecifier( configKeys );
        cspec_.addActionListener( forwarder_ );
        if ( configKeys.length > 0 ) {
            box.add( new LineBox( null, cspec_.getComponent(), true ) );
        }

        /* Place entry components for each required coordinate. */
        int nc = coords.length;
        colSelectors_ = new JComboBox[ nc ][];
        LabelledComponentStack stack = new LabelledComponentStack();
        for ( int ic = 0; ic < nc; ic++ ) {
            Input[] inputs = coords[ ic ].getInputs();
            int ni = inputs.length;
            colSelectors_[ ic ] = new JComboBox[ ni ];
            for ( int ii = 0; ii < ni; ii++ ) {
                InputMeta meta = inputs[ ii ].getMeta();
                final JComboBox cs = ColumnDataComboBoxModel.createComboBox();
                colSelectors_[ ic ][ ii ] = cs;
                cs.addActionListener( forwarder_ );
                JComponent line = Box.createHorizontalBox();
                line.add( cs );
                line.add( Box.createHorizontalStrut( 5 ) );
                line.add( new ComboBoxBumper( cs ) );

                /* Set the width to a small value, but add it to the stack
                 * with xfill true.  This has the effect of making it 
                 * fill the width of the panel, and not force horizontal
                 * scrolling until it's really small.  Since the panel is
                 * not basing its size on the preferred size of these
                 * components, that works out OK. */
                Dimension size = new Dimension( cs.getMinimumSize() );
                size.width = 80;
                cs.setMinimumSize( size );
                cs.setPreferredSize( cs.getMinimumSize() );
                stack.addLine( meta.getLongName(), null, line, true );

                /* Arrange for the coordinate entry labels to display tooltips
                 * giving the current value in a stilts-friendly format. */
                JLabel[] labels = stack.getLabels();
                final JLabel label = labels[ labels.length - 1 ];
                label.addMouseListener( InstantTipper.getInstance() );
                final String shortName = meta.getShortName();
                ActionListener tipListener = new ActionListener() {
                    public void actionPerformed( ActionEvent evt ) {
                        StringBuffer tbuf = new StringBuffer()
                            .append( shortName )
                            .append( '=' );
                        Object colItem = cs.getSelectedItem();
                        if ( colItem instanceof ColumnData ) {
                            tbuf.append( colItem.toString() );
                        }
                        label.setToolTipText( tbuf.toString() );
                    }
                };
                cs.addActionListener( tipListener );
                tipListener.actionPerformed( null );
            }
        }
        if ( nc > 0 ) {
            box.add( new LineBox( null, stack, true ) );
        }

        /* Place the lot at the top of the component so it doesn't fill
         * vertical space. */
        panel_.add( box, BorderLayout.NORTH );
    }

    /**
     * Returns the coordinates which this panel is getting values for.
     *
     * @return  coords
     */
    public Coord[] getCoords() {
        return coords_;
    }

    /**
     * Returns this panel's config specifier.
     *
     * @return  specifier for config values, if there are any
     */
    public ConfigSpecifier getConfigSpecifier() {
        return cspec_;
    }

    /**
     * Returns the config map associated with this panel.
     *
     * @return   result of <code>getConfigSpecifier().getSpecifiedValue()</code>
     */
    public ConfigMap getConfig() {
        return cspec_.getSpecifiedValue();
    }

    /**
     * Returns the graphical component for this object.
     *
     * @return  component
     */
    public JComponent getComponent() {
        return panel_;
    }

    /**
     * Adds a listener which will be notified when the coordinate selection
     * changes.
     *
     * @param  listener  listener
     */
    public void addActionListener( ActionListener listener ) {
        forwarder_.addActionListener( listener );
    }

    /**
     * Removes a listener which was added previously.
     *
     * @param  listener  listener
     */
    public void removeActionListener( ActionListener listener ) {
        forwarder_.removeActionListener( listener );
    }

    /**
     * Returns an object which will forward actions to listeners registered
     * with this panel.
     *
     * @return  action forwarder
     */
    public ActionListener getActionForwarder() {
        return forwarder_;
    }

    /**
     * Sets the table with reference to which this panel will resolve
     * coordinate descriptions.
     *
     * <p>If the existing selected coordinate values still make sense
     * (if the new table has sufficiently compatible column names),
     * they are retained.  If the columns cannot be retained they are
     * cleared, and in that case if the <code>autopopulate</code> parameter
     * is set, some default columns will be used.
     *
     * @param  tcModel   table from which coordinate values will be drawn
     * @param  autoPopulate   whether to autopopulate columns when old ones
     *                        can't be used or are absent
     */
    public void setTable( TopcatModel tcModel, boolean autoPopulate ) {
        tcModel_ = tcModel;
        int is = 1;
        int ninRequired = 0;
        int ninPopulated = 0;
        for ( int ic = 0; ic < coords_.length; ic++ ) {
            JComboBox[] colsels = colSelectors_[ ic ];
            Coord coord = coords_[ ic ];
            boolean isReq = coord.isRequired();
            Input[] inputs = coord.getInputs();
            int ni = colsels.length;
            if ( isReq ) {
                ninRequired += ni;
            }
            for ( int ii = 0; ii < ni; ii++ ) {
                InputMeta meta = inputs[ ii ].getMeta();
                JComboBox cs = colsels[ ii ];
                Object sel0 = cs.getSelectedItem();
                String str0 = sel0 instanceof ColumnData
                            ? sel0.toString()
                            : null;
                cs.setSelectedItem( null );
                if ( tcModel == null ) {
                    cs.setEnabled( false );
                }
                else {
                    ColumnDataComboBoxModel model =
                        new ColumnDataComboBoxModel( tcModel,
                                                     inputs[ ii ]
                                                    .getValueClass(), true );
                    cs.setModel( model );
                    cs.setEnabled( true );

                    /* If there was a previous value for the column,
                     * and if it can be used with the new table, re-use it. */
                    if ( str0 != null ) {
                        ColumnData cdata;
                        try {
                            cdata = model.stringToColumnData( str0 );
                        }
                        catch ( CompilationException e ) {
                            cdata = null;
                        }
                        if ( cdata != null ) {
                            model.setSelectedItem( cdata );
                            if ( isReq ) {
                                ninPopulated++;
                            }
                        }
                    }
                }
            }
        }

        /* Autopopulate only if none of the existing columns can be used.  
         * There are other possibilities, such as autopopulating those
         * columns which can't be re-used, but for now keep it simple. */
        if ( autoPopulate && ninPopulated == 0 && ninRequired > 0 ) {
            autoPopulate();
        }
    }

    /**
     * Makes some attempt to fill in the fields with non-blank values.
     * The default implementation fills in the first few suitable columns,
     * but subclasses are encouraged to override this behaviour if something
     * smarter is possible.
     */
    public void autoPopulate() {
        int is = 1;
        for ( int ic = 0; ic < coords_.length; ic++ ) {
            if ( coords_[ ic ].isRequired() ) {
                JComboBox[] colsels = colSelectors_[ ic ];
                for ( int iu = 0; iu < colsels.length; iu++ ) {
                    JComboBox cs = colsels[ iu ];
                    if ( is < cs.getItemCount() ) {
                        cs.setSelectedIndex( is++ );
                    }
                }
            }
        }
    }

    /**
     * Returns the currently configured topcat model.
     *
     * @return  table from most recent call to setTable
     */
    public TopcatModel getTable() {
        return tcModel_;
    }

    /**
     * Returns the coordinate values currently selected in this panel.
     * If there is insufficient information to contribute to a plot
     * (not all of the
     * {@link uk.ac.starlink.ttools.plot2.data.Coord#isRequired required}
     * coord values are filled in)
     * then null will be returned.
     *
     * @return   nCoord-element array of coord contents, or null
     */
    public GuiCoordContent[] getContents() {
        int npc = coords_.length;
        GuiCoordContent[] contents = new GuiCoordContent[ npc ];
        for ( int ic = 0; ic < npc; ic++ ) {
            Coord coord = coords_[ ic ];
            JComboBox[] colsels = colSelectors_[ ic ];
            int nu = colsels.length;
            ColumnData[] coldats = new ColumnData[ nu ];
            String[] datlabs = new String[ nu ];
            for ( int iu = 0; iu < nu; iu++ ) {
                Object colitem = colsels[ iu ].getSelectedItem();
                if ( colitem instanceof ColumnData ) {
                    coldats[ iu ] = (ColumnData) colitem;
                    datlabs[ iu ] = colitem.toString();
                }
                else if ( ! coord.isRequired() ) {
                    Input input = coord.getInputs()[ iu ];
                    ColumnInfo info =
                        new ColumnInfo( input.getMeta().getLongName(),
                                        input.getValueClass(),
                                        input.getMeta().getShortDescription() );
                    coldats[ iu ] = new ConstantColumn( info, null );
                    datlabs[ iu ] = null;
                }
                else {
                    return null;
                }
            }
            contents[ ic ] = new GuiCoordContent( coord, datlabs, coldats );
        }
        return contents;
    }

    /**
     * Returns the selector component model for a given user coordinate.
     * If no columndata-specific model has been set, null may be returned.
     *
     * @param  ic   coord index
     * @param  iu   user info index for the given coord
     * @return   selector model, or null
     */
    public ColumnDataComboBoxModel getColumnSelector( int ic, int iu ) {
        ComboBoxModel model = colSelectors_[ ic ][ iu ].getModel();
        return model instanceof ColumnDataComboBoxModel
             ? (ColumnDataComboBoxModel) model
             : null;
    }

    /**
     * Resets the selector component model for a given user coordinate.
     *
     * @param  ic   coord index
     * @param  iu   user info index for the given coord
     * @param  model  new selector model
     */
    public void setColumnSelector( int ic, int iu,
                                   ColumnDataComboBoxModel model ) {
        colSelectors_[ ic ][ iu ].setModel( model );
    }

    /**
     * Returns a list of column metadata items for the items in a
     * list model of columns.
     *
     * @param  model  column list model
     * @return  list of valueinfos
     */
    public static ValueInfo[] getInfos( ColumnDataComboBoxModel model ) {
        List<ValueInfo> list = new ArrayList<ValueInfo>();
        for ( int i = 0; i < model.getSize(); i++ ) {
            ColumnData cdata = model.getColumnDataAt( i );
            if ( cdata != null ) {
                ValueInfo info = cdata.getColumnInfo();
                if ( info != null ) {
                    list.add( info );
                }
            }
        }
        return list.toArray( new ValueInfo[ 0 ] );
    }

    /**
     * Tries to find an item of a given combo box model matching a given
     * metadata item.  If it finds it, it will set the selection and
     * return true.
     *
     * @param   model   list model
     * @param  info   template for selection value
     * @return  true if selection was successfully performed
     */
    public static boolean populate( ColumnDataComboBoxModel model,
                                     ValueInfo info ) {
        for ( int i = 0; i < model.getSize(); i++ ) {
            ColumnData cdata = model.getColumnDataAt( i );
            if ( cdata != null &&
                 infoMatches( cdata.getColumnInfo(), info ) ) {
                model.setSelectedItem( cdata );
                return true;
            }
        }
        return false;
    }

    /**
     * Indicates whether two infos match.
     * The criterion is that both name and UCD are the same.
     *
     * @param  info1  first item
     * @param  info2  second item
     * @return  true iff match
     */
    private static boolean infoMatches( ValueInfo info1, ValueInfo info2 ) {
        return PlotUtil.equals( info1.getName(), info2.getName() )
            && PlotUtil.equals( info1.getUCD(), info2.getUCD() );
    }
}
