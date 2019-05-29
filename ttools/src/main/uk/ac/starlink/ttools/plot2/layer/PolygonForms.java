package uk.ac.starlink.ttools.plot2.layer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.DefaultListCellRenderer;
import uk.ac.starlink.ttools.gui.ResourceIcon;
import uk.ac.starlink.ttools.plot2.PlotUtil;
import uk.ac.starlink.ttools.plot2.config.BooleanConfigKey;
import uk.ac.starlink.ttools.plot2.config.ComboBoxSpecifier;
import uk.ac.starlink.ttools.plot2.config.ConfigKey;
import uk.ac.starlink.ttools.plot2.config.ConfigMeta;
import uk.ac.starlink.ttools.plot2.config.ConfigMap;
import uk.ac.starlink.ttools.plot2.config.OptionConfigKey;
import uk.ac.starlink.ttools.plot2.config.Specifier;
import uk.ac.starlink.ttools.plot2.data.Coord;
import uk.ac.starlink.ttools.plot2.data.FloatingArrayCoord;
import uk.ac.starlink.ttools.plot2.data.InputMeta;

/**
 * ShapeForm implementations for plotting filled or outline polygons.
 *
 * @author   Mark Taylor
 * @since    5 Mar 2019
 */
public class PolygonForms {

    /** Shape form for a quadrilateral defined by 4 positional coordinates. */
    public static final ShapeForm QUAD = new FixedPolygonForm( 4 );

    /** Shape form for a polygon defined by an array coordinate. */
    public static final ShapeForm ARRAY = new ArrayPolygonForm();

    /** Polygon mode configuration key. */
    public static final ConfigKey<PolygonMode> POLYMODE_KEY =
        createPolygonModeKey();

    /** Speed-over-accuracy configuration key. */
    public static final ConfigKey<Boolean> ISFAST_KEY =
        new BooleanConfigKey(
            new ConfigMeta( "fast", "Fast" )
           .setShortDescription( "Faviour speed over accuracy?" )
           .setXmlDescription( new String[] {
                "<p>Determines whether speed is favoured over accuracy",
                "when drawing polygons.",
                "In some cases setting this false",
                "will give visually better results,",
                "and in some cases setting it true will speed up plotting.",
                "In other cases, the setting will make no difference",
                "to either.",
                "It may be necessary to set it false when filling",
                "polygons that may be re-entrant.",
                "</p>",
            } )
        , true );

    /** Coordinate for array coordinate, used with ARRAY. */
    public static final FloatingArrayCoord ARRAY_COORD =
        createArrayCoord();
                            
    /**
     * Private constructor prevents instantiation.
     */
    private PolygonForms() {
    }

    /**
     * Creates the array-valued coordinate object for use with the
     * ArrayPolygonForm.
     *
     * @return   array coord
     */
    private static FloatingArrayCoord createArrayCoord() {
        InputMeta meta = new InputMeta( "otherpoints", "Other Points" );
        meta.setShortDescription( "array of positions" );
        meta.setValueUsage( "array" );
        meta.setXmlDescription( new String[] {
            "<p>Array of coordinates giving the points of the other vertices",
            "(excluding the first one, which is defined by the positional",
            "coordinates of this dataset)",
            "that define the polygon to be drawn.",
            "These coordinates are given as an interleaved array",
            "by this parameter, e.g. (x2,y2, x3,y3, y4,y4).",
            "Some expression language functions that can be useful",
            "when specifying this parameter are",
            "<code>array()</code> and <code>parseDoubles()</code>.",
            "Although the first coordinate pair is supposed to be excluded",
            "from this array, if it's more convenient to include it",
            "in this list too, it doesn't usually affect the",
            "appearance of the plot.",
            "</p>",
        } );
        return FloatingArrayCoord.createCoord( meta, true );
    }

    /**
     * Creates the configuration key for polygon drawing mode.
     *
     * @return  mode key
     */
    private static ConfigKey<PolygonMode> createPolygonModeKey() {
        ConfigMeta meta = new ConfigMeta( "polymode", "Polygon Mode" );
        meta.setXmlDescription( new String[] {
            "<p>Polygon drawing mode.",
            "Different options are available, including drawing an outline",
            "round the edge and filling the interior with colour.",
            "</p>",
        } );
        final PolygonMode[] modes = PolygonMode.MODES;
        OptionConfigKey<PolygonMode> key =
                new OptionConfigKey<PolygonMode>( meta, PolygonMode.class,
                                                  modes ) {
            public String getXmlDescription( PolygonMode polyMode ) {
                return polyMode.getDescription();
            }
            @Override
            public Specifier<PolygonMode> createSpecifier() {
                JComboBox comboBox = new JComboBox( modes );
                comboBox.setRenderer( new PolygonModeRenderer() );
                return new ComboBoxSpecifier<PolygonMode>( PolygonMode.class,
                                                           comboBox );
            }
        };
        key.setOptionUsage();
        key.addOptionsXml();
        return key;
    }

    /**
     * ComboBox renderer for PolygonMode.  It draws a representation icon
     * as well as displaying the mode name.
     */
    private static class PolygonModeRenderer extends DefaultListCellRenderer {

        private int iconHeight_;
        private PolygonMode polyMode_;
        private final Icon icon_;

        /**
         * Constructor.
         */
        PolygonModeRenderer() {
            iconHeight_ = 0;
            icon_ = new Icon() {
                public int getIconWidth() {
                    return iconHeight_ * 2;
                }
                public int getIconHeight() {
                    return iconHeight_;
                }
                public void paintIcon( Component c, Graphics g, int x, int y ) {
                    if ( polyMode_ != null ) {
                        int w = getIconWidth();
                        int h = getIconHeight();
                        int[] xs = new int[] { x+0, x+w, x+w, x+0 };
                        int[] ys = new int[] { y+0, y+0, y+h, y+h };
                        Color color0 = g.getColor();
                        g.setColor( c.getForeground() );
                        polyMode_.getGlypher( false )
                                 .paintPolygon( g, xs, ys, 4 );
                        g.setColor( color0 );
                    }
                }
            };
        }

        @Override
        public Component getListCellRendererComponent( JList list, Object value,
                                                       int index, boolean isSel,
                                                       boolean hasFocus ) {
            Component c =
                super.getListCellRendererComponent( list, value, index,
                                                    isSel, hasFocus );
            if ( iconHeight_ <= 0 ) {
                iconHeight_ = c.getFontMetrics( c.getFont() ).getAscent();
            }
            if ( c instanceof JLabel && value instanceof PolygonMode ) {
                JLabel label = (JLabel) c;
                polyMode_ = (PolygonMode) value;
                label.setText( polyMode_.toString() );
                label.setIcon( icon_ );
            }
            return c;
        }
    }

    /**
     * ShapeForm implementation for a polygon defined by a fixed number
     * of positional coordinates.
     */
    private static class FixedPolygonForm implements ShapeForm {

        private final int np_;

        /**
         * Constructor.
         *
         * @param   np   number of vertices in polygon
         */
        public FixedPolygonForm( int np ) {
            np_ = np;
        }

        public String getFormName() {
            return "Poly" + np_;
        }

        public Icon getFormIcon() {
            return ResourceIcon.FORM_POLYLINE;
        }

        public String getFormDescription() {
            final String figname;
            if ( np_ == 3 ) {
                figname = "triangle";
            }
            else if ( np_ == 4 ) {
                figname = "quadrilateral";
            }
            else {
                figname = Integer.toString( np_ ) + "-sided polygon";
            }
            return PlotUtil.concatLines( new String[] {
                "<p>Draws a closed " + figname,
                "given the coordinates of its vertices",
                "supplied as " + np_ + " separate positions.",
                "The way that the polygon is drawn (outline, fill etc)",
                "is determined using the",
                "<code>" + POLYMODE_KEY.getMeta().getShortName() + "</code>",
                "option.",
                "</p>",
            } );
        }

        public int getPositionCount() {
            return np_;
        }

        public Coord[] getExtraCoords() {
            return new Coord[ 0 ];
        }

        public ConfigKey[] getConfigKeys() {
            return new ConfigKey[] {
                POLYMODE_KEY,
                ISFAST_KEY,
            };
        }

        public Outliner createOutliner( ConfigMap config ) {
            PolygonMode polyMode = config.get( POLYMODE_KEY );
            boolean isFast = config.get( ISFAST_KEY ).booleanValue();
            PolygonMode.Glypher polyGlypher = polyMode.getGlypher( isFast );
            return PolygonOutliner.createFixedOutliner( np_, polyGlypher );
        }
    }

    /**
     * ShapeForm implementation for a polygon defined by one positional
     * coordinate defining the first vertex and an array-valued coordinate
     * for the other vertices.
     * This looks a bit clunky from a UI point of view,
     * but the rest of the plotting system really wants at least
     * one positional coordinate for any plot type that's plotting
     * objects at positions, so trying to do it using (for instance)
     * an array-valued coordinate supplying all of the vertices
     * means a lot of things don't work well.
     */
    private static class ArrayPolygonForm implements ShapeForm {

        public String getFormName() {
            return "Polygon";
        }

        public Icon getFormIcon() {
            return ResourceIcon.FORM_POLYLINE;
        }

        public String getFormDescription() {
            String arrayCoordName =
                ARRAY_COORD.getInputs()[ 0 ].getMeta().getShortName();
            return PlotUtil.concatLines( new String[] {
                "<p>Draws a closed polygon given an array of coordinates",
                "that define its vertices.",
                "In fact this plot requires the position of the first vertex",
                "supplied as a positional value in the usual way",
                "(e.g. <code>X</code> and <code>Y</code> coordinates)",
                "and the second, third etc vertices supplied as an array",
                "using the <code>" + arrayCoordName + "</code> parameter.",
                "</p>",
                "<p>Invocation might therefore look like",
                "\"<code>xN=x1 yN=y1 " + arrayCoordName + "N="
                                       + "array(x2,y2, x3,y3, x4,y4)</code>\".",
                "</p>",
            } );
        }

        public int getPositionCount() {
            return 1;
        }

        public Coord[] getExtraCoords() {
            return new Coord[] { ARRAY_COORD, };
        }

        public ConfigKey[] getConfigKeys() {
            return new ConfigKey[] {
                POLYMODE_KEY,
                ISFAST_KEY,
            };
        }

        public Outliner createOutliner( ConfigMap config ) {
            PolygonMode polyMode = config.get( POLYMODE_KEY );
            boolean isFast = config.get( ISFAST_KEY ).booleanValue();
            PolygonMode.Glypher glypher = polyMode.getGlypher( isFast );
            return PolygonOutliner.createArrayOutliner( ARRAY_COORD, glypher );
        }
    }
}
