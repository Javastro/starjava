package uk.ac.starlink.ttools.plot2.config;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.ListModel;
import uk.ac.starlink.ttools.gui.MarkStyleSelectors;
import uk.ac.starlink.ttools.gui.ThicknessComboBox;
import uk.ac.starlink.ttools.plot.BarStyle;
import uk.ac.starlink.ttools.plot.BarStyles;
import uk.ac.starlink.ttools.plot.ErrorMode;
import uk.ac.starlink.ttools.plot.ErrorRenderer;
import uk.ac.starlink.ttools.plot.MarkShape;
import uk.ac.starlink.ttools.plot.MarkStyle;
import uk.ac.starlink.ttools.plot.Shader;
import uk.ac.starlink.ttools.plot.Shaders;
import uk.ac.starlink.ttools.plot.Styles;
import uk.ac.starlink.ttools.plot2.Anchor;
import uk.ac.starlink.ttools.plot2.PlotUtil;
import uk.ac.starlink.ttools.plot2.Scaling;
import uk.ac.starlink.ttools.plot2.Subrange;
import uk.ac.starlink.ttools.plot2.geom.PlaneSurfaceFactory;
import uk.ac.starlink.ttools.plot2.layer.Combiner;
import uk.ac.starlink.ttools.plot2.layer.FillMode;
import uk.ac.starlink.ttools.plot2.layer.LevelMode;
import uk.ac.starlink.ttools.plot2.layer.Normalisation;
import uk.ac.starlink.ttools.plot2.layer.XYShape;
import uk.ac.starlink.ttools.plot2.layer.XYShapes;
import uk.ac.starlink.util.gui.RenderingComboBox;

/**
 * Contains many common config keys and associated utility methods.
 *
 * @author   Mark Taylor
 * @since    25 Feb 2013
 */
public class StyleKeys {

    private static final MarkShape[] SHAPES = createShapes();

    /** Config key for marker shape. */
    public static final ConfigKey<MarkShape> MARK_SHAPE =
        new OptionConfigKey<MarkShape>(
            new ConfigMeta( "shape", "Shape" )
           .setShortDescription( "Marker shape" )
           .setXmlDescription( new String[] {
                "<p>Sets the shape of markers that are plotted at each",
                "position of the scatter plot.",
                "</p>",
            } )
        , MarkShape.class, SHAPES, MarkShape.FILLED_CIRCLE ) {
        public String getXmlDescription( MarkShape shape ) {
            return null;
        }
        public Specifier<MarkShape> createSpecifier() {
            return new ComboBoxSpecifier<MarkShape>( MarkStyleSelectors
                                                    .createShapeSelector() );
        }
    }.setOptionUsage()
     .addOptionsXml();

    /** Config key for marker size. */
    public static final ConfigKey<Integer> SIZE =
        new IntegerConfigKey(
            new ConfigMeta( "size", "Size" )
           .setStringUsage( "<pixels>" )
           .setShortDescription( "Marker size in pixels" )
           .setXmlDescription( new String[] {
                "<p>Size of the scatter plot markers.",
                "The unit is pixels, in most cases the marker is approximately",
                "twice the size of the supplied value.",
                "</p>",
            } )
        , 1 ) {
        public Specifier<Integer> createSpecifier() {
            return new ComboBoxSpecifier<Integer>( MarkStyleSelectors
                                                  .createSizeSelector() );
        }
    };

    private static final XYShape[] XYSHAPES = XYShapes.getXYShapes();

    /** Config key for XY shape. */
    public static final ConfigKey<XYShape> XYSHAPE =
        new OptionConfigKey<XYShape>(
            new ConfigMeta( "shape", "Shape" )
           .setShortDescription( "Marker shape" )
           .setXmlDescription( new String[] {
            } )
        , XYShape.class, XYSHAPES ) {
        public String getXmlDescription( XYShape shape ) {
            return null;
        }
        public Specifier<XYShape> createSpecifier() {
            JComboBox shapeSelector = new RenderingComboBox( XYSHAPES ) {
                @Override
                protected Icon getRendererIcon( Object shape ) {
                    return XYShape.createIcon( (XYShape) shape, 20, 12, true );
                }
                protected String getRendererText( Object shape ) {
                    return null;
                }
            };
            return new ComboBoxSpecifier<XYShape>( shapeSelector );
        }
    }.setOptionUsage()
     .addOptionsXml();

    /** Config key for style colour. */
    public static final ConfigKey<Color> COLOR =
        new ColorConfigKey( ColorConfigKey
                           .createColorMeta( "color", "Color", "plotted data" ),
                            ColorConfigKey.COLORNAME_RED, false );

    /** Config key for the opacity limit of transparent plots.
     *  This is the number of times a point has to be hit to result in
     *  a saturated (opaque) pixel. */
    public static final ConfigKey<Double> OPAQUE =
        DoubleConfigKey.createSliderKey(
            new ConfigMeta( "opaque", "Opaque limit" )
           .setShortDescription( "Fraction of fully opaque" )
           .setXmlDescription( new String[] {
                "<p>The opacity of plotted points.",
                "The value is the number of points which have to be",
                "overplotted before the background is fully obscured.",
                "</p>",
            } )
        , 4, 1, 10000, true );

    /** Config key for the opacity limit of auxiliary shaded plots. */
    public static final ConfigKey<Double> AUX_OPAQUE =
        DoubleConfigKey.createSliderKey(
            new ConfigMeta( "opaque", "Opaque limit" )
           .setShortDescription( "Aux fraction of fully opaque" )
           .setXmlDescription( new String[] {
                "<p>The opacity of points plotted in the Aux colour.",
                "The value is the number of points which have to be",
                "overplotted before the background is fully obscured.",
                "</p>",
            } )
        , 1, 1, 1000, true );

    /** Config key for transparency level of adaptive transparent plots. */
    public static final ConfigKey<Double> TRANSPARENT_LEVEL =
        DoubleConfigKey.createSliderKey(
            new ConfigMeta( "translevel", "Transparency Level" )
           .setShortDescription( "Transparency" )
           .setXmlDescription( new String[] {
                "<p>Sets the level of automatically controlled transparency. ",
                "The higher this value the more transparent points are.",
                "Exactly how transparent points are depends on how many",
                "are currently being plotted on top of each other and",
                "the value of this parameter.",
                "The idea is that you can set it to some fixed value,",
                "and then get something which looks similarly",
                "transparent while you zoom in and out.",
                "</p>",
            } )
        , 0.1, 0.001, 2, true );

    /** Config key for "normal" transparency - it's just 1-alpha. */
    public static final ConfigKey<Double> TRANSPARENCY =
        DoubleConfigKey.createSliderKey(
            new ConfigMeta( "transparency", "Transparency" )
           .setShortDescription( "Fractional transparency" )
           .setXmlDescription( new String[] {
                "<p>Transparency with which compoents are plotted,",
                "in the range 0 (opaque) to 1 (invisible).",
                "The value is 1-alpha.",
                "</p>",
            } )
           .setStringUsage( "0..1" )
        , 0, 0, 1, false );

    /** Config key for line thickness. */
    private static final ConfigKey<Integer> THICKNESS = createThicknessKey( 1 );

    /** Config key for line dash style. */
    public static final ConfigKey<float[]> DASH =
        new DashConfigKey( DashConfigKey.createDashMeta( "dash", "Dash" ) );

    /** Config key for axis grid colour. */
    public static final ConfigKey<Color> GRID_COLOR =
        new ColorConfigKey( ColorConfigKey
                           .createColorMeta( "gridcolor", "Grid Color",
                                             "the plot grid" ),
                            ColorConfigKey.COLORNAME_LIGHTGREY, false );

    /** Config key for axis label colour. */
    public static final ConfigKey<Color> AXLABEL_COLOR =
        new ColorConfigKey(
            ColorConfigKey 
           .createColorMeta( "labelcolor", "Label Color",
                             "axis labels and other plot annotations" )
            , ColorConfigKey.COLORNAME_BLACK, false );

    /** Config key for density map combination. */
    public static final ConfigKey<Combiner> COMBINER =
        new OptionConfigKey<Combiner>(
            new ConfigMeta( "combine", "Combine" )
           .setShortDescription( "Value combination mode" )
           .setXmlDescription( new String[] {
                "<p>Defines how values contributing to the same",
                "density map bin are combined together to produce",
                "the value assigned to that bin (and hence its colour).",
                "</p>",
                "<p>For unweighted values (a pure density map),",
                "it usually makes sense to use",
                "<code>" + Combiner.COUNT + "</code>.",
                "However, if the input is weighted by an additional",
                "data coordinate, one of the other values such as",
                "<code>" + Combiner.MEAN + "</code>",
                "may be more revealing.",
                "</p>",
            } )
        , Combiner.class, Combiner.getKnownCombiners(), Combiner.SUM ) {
        public String getXmlDescription( Combiner combiner ) {
            return combiner.getDescription();
        }
    };

    private static final BarStyle.Form[] BARFORMS = new BarStyle.Form[] {
        BarStyle.FORM_OPEN,
        BarStyle.FORM_FILLED,
        BarStyle.FORM_SEMIFILLED,
        BarStyle.FORM_TOP,
        BarStyle.FORM_SEMITOP,
        BarStyle.FORM_SPIKE,
    };

    /** Config key for histogram bar style. */
    public static final ConfigKey<BarStyle.Form> BAR_FORM =
        new OptionConfigKey<BarStyle.Form>(
            new ConfigMeta( "barform", "Bar Form" )
           .setShortDescription( "Histogram bar shape" )
           .setXmlDescription( new String[] {
                "<p>How histogram bars are represented.",
                "Note that options using transparent colours",
                "may not render very faithfully",
                "to some vector formats like PDF and EPS.",
                "</p>",
            } )
        , BarStyle.Form.class, BARFORMS, BarStyle.FORM_SEMIFILLED ) {
            public String getXmlDescription( BarStyle.Form barForm ) {
                return null;
            }
            public Specifier<BarStyle.Form> createSpecifier() {
                JComboBox formSelector = new RenderingComboBox( BARFORMS ) {
                    protected Icon getRendererIcon( Object form ) {
                        return BarStyles.getIcon( (BarStyle.Form) form );
                    }
                };
                return new ComboBoxSpecifier<BarStyle.Form>( formSelector );
            }
        }.setOptionUsage()
         .addOptionsXml();

    private static final FillMode[] FILLMODES = new FillMode[] {
        FillMode.SOLID, FillMode.LINE, FillMode.SEMI,
    };
    private static final int[] FILLMODE_ICON_DATA = new int[] {
        1, 2, 3, 3, 4, 5, 6, 7, 8, 9, 9, 7, 8, 7, 5, 5,
        6, 7, 8, 9, 11, 11, 10, 11, 12, 11, 9, 7, 5, 4, 2, 1, 1, 0,
    };

    /** Config key for KDE fill mode. */
    public static final ConfigKey<FillMode> FILL =
        new OptionConfigKey<FillMode>(
            new ConfigMeta( "fill", "Fill" )
           .setShortDescription( "Fill mode" )
           .setXmlDescription( new String[] {
                "<p>How the density function is represented.",
                "</p>",
            } )
            , FillMode.class, FILLMODES, FillMode.SEMI
         ) {
            public String getXmlDescription( FillMode fillMode ) {
                return fillMode.getDescription();
            }
            public Specifier<FillMode> createSpecifier() {
                JComboBox fillSelector = new RenderingComboBox( FILLMODES ) {
                    protected Icon getRendererIcon( Object fillmode ) {
                        return ((FillMode) fillmode)
                              .createIcon( FILLMODE_ICON_DATA, Color.BLACK,
                                           new BasicStroke(), 2 );
                    }
                };
                return new ComboBoxSpecifier<FillMode>( fillSelector );
            }
         }.setOptionUsage()
          .addOptionsXml();

    /** Config key for cumulative histogram flag. */
    public static final ConfigKey<Boolean> CUMULATIVE =
        new BooleanConfigKey(
            new ConfigMeta( "cumulative", "Cumulative" )
           .setShortDescription( "Cumulative histogram?" )
           .setXmlDescription( new String[] {
                "<p>If true, the histogram bars plotted are calculated",
                "cumulatively;",
                "each bin includes the counts from all previous bins.",
                "</p>",
            } )
        );

    /** Config key for histogram normalisation mode. */
    public static final ConfigKey<Normalisation> NORMALISE =
        new OptionConfigKey<Normalisation>(
            new ConfigMeta( "normalise", "Normalise" )
           .setShortDescription( "Normalisation mode" )
           .setXmlDescription( new String[] {
                "<p>Defines how, if at all, the bars of histogram-like plots",
                "are normalised.",
                "</p>",
            } )
        , Normalisation.class, Normalisation.values(), Normalisation.NONE ) {
            public String getXmlDescription( Normalisation norm ) {
                return norm.getDescription();
            }
        }.setOptionUsage()
         .addOptionsXml();


    /** Config key for line antialiasing. */
    public static final ConfigKey<Boolean> ANTIALIAS =
        new BooleanConfigKey(
            new ConfigMeta( "antialias", "Antialiasing" )
           .setShortDescription( "Antialias lines?" )
           .setXmlDescription( new String[] {
                "<p>If true, plotted lines are drawn with antialising.",
                "Antialised lines look smoother, but may take",
                "perceptibly longer to draw.",
                "Only has any effect for bitmapped output formats.",
                "</p>",
            } )
        , false );

    /** Config key for axis grid antialiasing. */
    public static final ConfigKey<Boolean> GRID_ANTIALIAS =
        new BooleanConfigKey(
            new ConfigMeta( "gridaa", "Antialiasing" )
           .setShortDescription( "Use antialiasing for grid lines?" )
           .setXmlDescription( new String[] {
                "<p>If true, grid lines are drawn with antialiasing.",
                "Antialiased lines look smoother, but may take",
                "perceptibly longer to draw.",
                "Only has any effect for bitmapped output formats.",
                "</p>",
            } )
        , false );

    /** Config key for text anchor positioning. */
    public static final ConfigKey<Anchor> ANCHOR =
        new OptionConfigKey<Anchor>(
            new ConfigMeta( "anchor", "Anchor" )
           .setShortDescription( "Text label anchor position" )
           .setXmlDescription( new String[] {
                "<p>Determines where the text appears",
                "in relation to the plotted points.",
                "Values are points of the compass.",
                "</p>",
            } )
        , Anchor.class, new Anchor[] { Anchor.W, Anchor.E, Anchor.N, Anchor.S, }
        ) {
           public String getXmlDescription( Anchor anchor ) {
               return null;
           }
        }.setOptionUsage()
         .addOptionsXml();

    /** Config key for scaling level mode. */ 
    public static final ConfigKey<LevelMode> LEVEL_MODE =
        new OptionConfigKey<LevelMode>(
            new ConfigMeta( "scaling", "Scaling" )
           .setShortDescription( "Level scaling" )
           .setXmlDescription( new String[] {
                "<p>How the smoothed density is treated before",
                "contour levels are determined.",
                "</p>",
            } )
            , LevelMode.class, LevelMode.MODES, LevelMode.LINEAR
        ) {
            public String getXmlDescription( LevelMode mode ) {
                return mode.getDescription();
            }
        }.setOptionUsage()
         .addOptionsXml();

    /** Config key for vector marker style. */
    public static final MultiPointConfigKey VECTOR_SHAPE =
        createMultiPointKey( "arrow", "Arrow", ErrorRenderer.getOptionsVector(),
                             new ErrorMode[] { ErrorMode.UPPER } );

    /** Config key for ellipse marker style. */
    public static final MultiPointConfigKey ELLIPSE_SHAPE =
        createMultiPointKey( "ellipse", "Ellipse",
                             ErrorRenderer.getOptionsEllipse(),
                             new ErrorMode[] { ErrorMode.SYMMETRIC,
                                               ErrorMode.SYMMETRIC } );

    /** Config key for 1d (vertical) error marker style. */
    public static final MultiPointConfigKey ERROR_SHAPE_1D =
        createMultiPointKey( "errorbar", "Error Bar",
                             ErrorRenderer.getOptions1d(),
                             new ErrorMode[] { ErrorMode.SYMMETRIC } );

    /** Config key for 2d error marker style. */
    public static final MultiPointConfigKey ERROR_SHAPE_2D =
        createMultiPointKey( "errorbar", "Error Bar",
                             ErrorRenderer.getOptions2d(),
                             new ErrorMode[] { ErrorMode.SYMMETRIC,
                                               ErrorMode.SYMMETRIC } );

    /** Config key for 3d error marker style. */
    public static final MultiPointConfigKey ERROR_SHAPE_3D =
        createMultiPointKey( "errorbar", "Error Bar",
                             ErrorRenderer.getOptions3d(),
                             new ErrorMode[] { ErrorMode.SYMMETRIC,
                                               ErrorMode.SYMMETRIC,
                                               ErrorMode.SYMMETRIC } );

    /** Config key for aux axis tick crowding. */
    public static final ConfigKey<Double> AUX_CROWD =
        PlaneSurfaceFactory.createAxisCrowdKey( "Aux" );

    /** Config key for aux shader lower limit. */
    public static final ConfigKey<Double> SHADE_LOW =
        PlaneSurfaceFactory.createAxisLimitKey( "Aux", false );

    /** Config key for aux shader upper limit. */
    public static final ConfigKey<Double> SHADE_HIGH =
        PlaneSurfaceFactory.createAxisLimitKey( "Aux", true );

    /** Config key for aux shader subrange. */
    public static final ConfigKey<Subrange> SHADE_SUBRANGE =
        new SubrangeConfigKey( SubrangeConfigKey
                              .createAxisSubMeta( "aux", "Aux" ) );

    /** Config key for aux null colour. */
    public static final ConfigKey<Color> AUX_NULLCOLOR =
        createNullColorKey( "aux", "Aux" );

    private static final String SCALE_NAME = "scale";
    private static final String AUTOSCALE_NAME = "autoscale";

    /** Config key for scaling of markers in data space. */
    public static final ConfigKey<Double> SCALE =
        new DoubleConfigKey(
            new ConfigMeta( SCALE_NAME, "Scale" )
           .setStringUsage( "<factor>" )
           .setShortDescription( "Marker size multiplier" )
           .setXmlDescription( new String[] {
                "<p>Affects the size of variable-sized markers",
                "like vectors and ellipses.",
                "The default value is 1, smaller or larger values",
                "multiply the visible sizes accordingly.",
                "</p>",
            } )
        , 1.0 ) {
            public Specifier<Double> createSpecifier() {
                return new SliderSpecifier( 1e-4, 1e+4, true, 1.0, false,
                                            SliderSpecifier.TextOption
                                                           .ENTER_ECHO );
            }
        };

    /** Config key for scaling of markers in pixel space. */
    public static final ConfigKey<Double> SCALE_PIX =
        new DoubleConfigKey(
            new ConfigMeta( SCALE_NAME, "Scale" )
           .setStringUsage( "<factor>" )
           .setShortDescription( "Marker size multiplier" )
           .setXmlDescription( new String[] {
                "<p>Scales the size of variable-sized markers.",
                "The default is 1, smaller or larger values",
                "multiply the visible sizes accordingly.",
                "</p>",
            } )
        , 1.0 ) {
            public Specifier<Double> createSpecifier() {
                return new SliderSpecifier( 1e-2, 1e+2, true, 1.0, false,
                                            SliderSpecifier.TextOption
                                                           .ENTER_ECHO );
            }
        };

    /** Config key for autoscale flag for markers in data space. */
    public static final ConfigKey<Boolean> AUTOSCALE =
        new BooleanConfigKey(
            new ConfigMeta( AUTOSCALE_NAME, "Auto Scale" )
           .setShortDescription( "Scale marker sizes automatically?" )
           .setXmlDescription( new String[] {
                "<p>Determines whether the default size of variable-sized",
                "markers like vectors and ellipses are automatically",
                "scaled to have a sensible size.",
                "If true, then the sizes of all the plotted markers",
                "are examined, and some dynamically calculated factor is",
                "applied to them all to make them a sensible size",
                "(by default, the largest ones will be a few tens of pixels).",
                "If false, the sizes will be the actual input values",
                "interpreted in data coordinates.",
                "</p>",
                "<p>If auto-scaling is on, then markers will keep",
                "approximately the same screen size during zoom operations;",
                "if it's off, they will keep the same size",
                "in data coordinates.",
                "</p>",
                "<p>Marker size is also affected by the",
                "<code>" + SCALE_NAME + "</code> parameter.",
                "</p>",
            } )
        , Boolean.TRUE );

    /** Config key for autoscale flag for markers in pixel space. */
    public static final ConfigKey<Boolean> AUTOSCALE_PIX =
        new BooleanConfigKey(
            new ConfigMeta( AUTOSCALE_NAME, "Auto Scale" )
           .setShortDescription( "Scale marker sizes automatically?" )
           .setXmlDescription( new String[] {
                "<p>Determines whether the basic size",
                "of variable sized markers is automatically",
                "scaled to have a sensible size.",
                "If true, then the sizes of all the plotted markers",
                "are examined, and some dynamically calculated factor is",
                "applied to them all to make them a sensible size",
                "(by default, the largest ones will be a few tens of pixels).",
                "If false, the sizes will be the actual input values",
                "in units of pixels.",
                "</p>",
                "<p>If auto-scaling is off, then markers will keep",
                "exactly the same screen size during pan and zoom operations;",
                "if it's on, then the visible sizes will change according",
                "to what other points are currently plotted.",
                "</p>",
                "<p>Marker size is also affected by the",
                "<code>" + SCALE_NAME + "</code> parameter.",
                "</p>",
            } )
        , Boolean.TRUE );

    /** Config key for a layer label string. */
    public static final ConfigKey<String> LABEL =
        new StringConfigKey(
            new ConfigMeta( "label", "Label" )
           .setStringUsage( "<txt>" )
           .setShortDescription( "Plot layer label" )
           .setXmlDescription( new String[] {
                "<p>Supplies a text label for a plot layer.",
                "This may be used for identifying it in the legend.",
                "If not supplied, a label will be generated automatically",
                "where required.",
                "</p>",
            } )
        , null );

    /** Config key for legend inclusion flag. */
    public static final ConfigKey<Boolean> SHOW_LABEL =
        new BooleanConfigKey(
            new ConfigMeta( "inlegend", "In Legend" )
           .setShortDescription( "Show layer in legend?" )
           .setXmlDescription( new String[] {
                "<p>Determines whether the layer has an entry in the",
                "plot legend, if shown.",
                "</p>",
            } )
        , true );

    /** Config key for minor tick drawing key. */
    public static final ConfigKey<Boolean> MINOR_TICKS =
        new BooleanConfigKey(
            new ConfigMeta( "minor", "Minor Ticks" )
           .setShortDescription( "Display minor tick marks?" )
           .setXmlDescription( new String[] {
                "<p>If true, minor tick marks are painted along the axes",
                "as well as the major tick marks.",
                "Minor tick marks do not have associated grid lines.",
                "</p>",
            } )
        , true );

    /** Config key for zoom factor. */
    public static final ConfigKey<Double> ZOOM_FACTOR =
        DoubleConfigKey.createSliderKey(
            new ConfigMeta( "zoomfactor", "Zoom Factor" )
           .setShortDescription( "Amount of zoom per mouse wheel unit" )
           .setXmlDescription( new String[] {
                "<p>Sets the amount by which the plot view zooms in or out",
                "for each unit of mouse wheel movement.",
                "A value of 1 means that mouse wheel zooming has no effect.",
                "A higher value means that the mouse wheel zooms faster",
                "and a value nearer 1 means it zooms slower.",
                "Values below 1 are not permitted.",
                "</p>",
            } )
        , 1.2, 1, 2, true );

    /** Config key set for axis and general captioner. */
    public static final CaptionerKeySet CAPTIONER = new CaptionerKeySet();

    /** Config key set for global Aux axis colour ramp. */
    public static final RampKeySet AUX_RAMP =
        new RampKeySet( "aux", "Aux",
                        createAuxShaders(), Scaling.LINEAR, false );

    /** Config key set for density point shading. */
    public static final RampKeySet DENSITY_RAMP =
        new RampKeySet( "dense", "Density",
                        createDensityShaders(), Scaling.LOG, true );

    /** Config key set for density map shading. */
    public static final RampKeySet DENSEMAP_RAMP =
        new RampKeySet( "dense", "Density",
                        createDensityMapShaders(), Scaling.LINEAR, true );

    /** Config key set for spectrogram shading. */
    public static final RampKeySet SPECTRO_RAMP =
        new RampKeySet( "spectro", "Spectral",
                        createAuxShaders(), Scaling.LINEAR, true );

    /**
     * Private constructor prevents instantiation.
     */
    private StyleKeys() {
    }

    /**
     * Returns a list of config keys for configuring a line-drawing stroke.
     * Pass a map with values for these to the <code>createStroke</code>
     * method.
     *
     * @return  stroke key list
     * @see  #createStroke
     */
    public static ConfigKey[] getStrokeKeys() {
        return new ConfigKey[] {
            THICKNESS,
            DASH,
        };
    }

    /**
     * Obtains a line drawing stroke based on a config map.
     * The keys used are those returned by <code>getStrokeKeys</code>.
     * The line join and cap policy must be provided.
     *
     * @param  config  config map
     * @param  cap     one of {@link java.awt.BasicStroke}'s CAP_* constants
     * @param  join    one of {@link java.awt.BasicStroke}'s JOIN_* constants
     * @return  stroke
     */
    public static Stroke createStroke( ConfigMap config, int cap, int join ) {
        int thick = config.get( THICKNESS );
        float[] dash = config.get( DASH );
        if ( dash != null && thick != 1 ) {
            dash = (float[]) dash.clone();
            for ( int i = 0; i < dash.length; i++ ) {
                dash[ i ] *= thick;
            }
        }
        return new BasicStroke( thick, cap, join, 10f, dash, 0f );
    }

    /**
     * Returns an axis tick mark crowding config key.
     *
     * @param  meta  metadata
     * @return   new key
     */
    public static ConfigKey<Double> createCrowdKey( ConfigMeta meta ) {
        return DoubleConfigKey.createSliderKey( meta, 1, 0.125, 10, true );
    }

    /**
     * Returns an axis labelling config key.
     *
     * @param  axName  axis name
     * @return  new key
     */
    public static ConfigKey<String> createAxisLabelKey( String axName ) {
        ConfigMeta meta = new ConfigMeta( axName.toLowerCase() + "label",
                                          axName + " Label" );
        meta.setStringUsage( "<text>" );
        meta.setShortDescription( "Label for axis " + axName );
        meta.setXmlDescription( new String[] {
            "<p>Gives a label to be used for annotating axis " + axName,
            "A default value based on the plotted data will be used",
            "if no value is supplied.",
            "</p>",
        } );
        return new StringConfigKey( meta, axName );
    }

    /**
     * Returns a key for acquiring a colour used in place of a shading ramp
     * colour in case that the input data is null.
     *
     * @param  axname  short form of axis name, used in text parameter names
     * @param  axName  long form of axis name, used in descriptions
     * @return  new key
     */
    public static ConfigKey<Color> createNullColorKey( String axname,
                                                       String axName ) {
        return new ColorConfigKey(
            ColorConfigKey.createColorMeta( axname.toLowerCase() + "nullcolor",
                                            "Null Color",
                                            "points with a null value of the "
                                          + axName + " coordinate" )
           .appendXmlDescription( new String[] {
                "<p>If the value is null, then points with a null",
                axName,
                "value will not be plotted at all.",
                "</p>",
            } )
            , ColorConfigKey.COLORNAME_GREY, true );
    }

    /**
     * Returns a config key for line thickness with a given default value.
     *
     * @param  dfltThick  default value for line width in pixels
     * @return   new config key
     */
    public static ConfigKey<Integer> createThicknessKey( int dfltThick ) {
        ConfigMeta meta = new ConfigMeta( "thick", "Thickness" );
        meta.setStringUsage( "<pixels>" );
        meta.setShortDescription( "Line thickness in pixels" );
        meta.setXmlDescription( new String[] {
            "<p>Thickness of plotted line in pixels.",
            "</p>",
        } );
        return new IntegerConfigKey( meta, dfltThick ) {
            public Specifier<Integer> createSpecifier() {
                return new ComboBoxSpecifier<Integer>(
                               new ThicknessComboBox( 5 ) );
            }
        };
    }

    /**
     * Creates a config key for a multipoint shape.
     *
     * @param   shortName   one-word name
     * @param   longName   GUI name
     * @param   renderers   renderer options
     * @param   modes   error mode objects, used with renderers to draw icon
     * @return  new key
     */
    private static MultiPointConfigKey
            createMultiPointKey( String shortName, String longName,
                                 ErrorRenderer[] renderers,
                                 ErrorMode[] modes ) {
        ConfigMeta meta = new ConfigMeta( shortName, longName );
        meta.setShortDescription( longName + " shape" );
        meta.setXmlDescription( new String[] {
            "<p>How " + shortName + "s are represented.",
            "</p>",
        } );
        MultiPointConfigKey key =
            new MultiPointConfigKey( meta, renderers, modes );
        key.setOptionUsage();
        key.addOptionsXml();
        return key;
    }

    /**
     * Returns an array of marker shapes.
     *
     * @return  marker shapes
     */
    private static MarkShape[] createShapes() {
        return new MarkShape[] {
            MarkShape.FILLED_CIRCLE,
            MarkShape.OPEN_CIRCLE,
            MarkShape.CROSS,
            MarkShape.CROXX,
            MarkShape.OPEN_SQUARE,
            MarkShape.OPEN_DIAMOND,
            MarkShape.OPEN_TRIANGLE_UP,
            MarkShape.OPEN_TRIANGLE_DOWN,
            MarkShape.FILLED_SQUARE,
            MarkShape.FILLED_DIAMOND,
            MarkShape.FILLED_TRIANGLE_UP,
            MarkShape.FILLED_TRIANGLE_DOWN,
        };
    }

    /**
     * Returns a list of shaders suitable for density point shading.
     *
     * @return  shaders
     */
    private static Shader[] createDensityShaders() {
        List<Shader> list = new ArrayList<Shader>();
        list.add( clip( Shaders.FADE_BLACK, 0, false ) );
        list.add( clip( Shaders.FADE_WHITE, 0.1, false ) );
        list.addAll( Arrays.asList( createColorShaders( true ) ) );
        return list.toArray( new Shader[ 0 ] );
    }

    /**
     * Returns a list of shaders suitable for density map shading.
     *
     * @return  shaders
     */
    private static Shader[] createDensityMapShaders() {
        List<Shader> list = new ArrayList<Shader>();
        for ( Shader shader : createColorShaders( false ) ) {
            if ( shader.isAbsolute() ) {
                list.add( shader );
            }
        }
        return list.toArray( new Shader[ 0 ] );
    }

    /**
     * Returns a list of shaders suitable for aux axis shading.
     *
     * @return  shaders
     */
    public static Shader[] createAuxShaders() {
        List<Shader> list = new ArrayList<Shader>();
        list.addAll( Arrays.asList( createColorShaders( true ) ) );
        list.addAll( Arrays.asList( new Shader[] {
            Shaders.createMaskShader( "Mask", 0f, 1f, true ),
            clip( Shaders.FADE_BLACK, 0, false ),
            clip( Shaders.FADE_WHITE, 0.1, false ),
            clip( Shaders.TRANSPARENCY, 0.1, false ),
        } ) );
        return list.toArray( new Shader[ 0 ] );
    }

    /**
     * Returns a generic list of shaders suitable for all purposes.
     * They are provided in a reasonably uniform way; where applicable
     * the "lighter" end is near zero.
     * If the <code>isAllVisible</code> flag is set true,
     * then the resulting shaders will (where possible) supply a colour
     * range which is visually distinguishable from white over its
     * entire range.
     * 
     * @param  isAllVisible  if true, tweaks are applied as necesary
     *         so that all the whole range is distinguishable from white
     * @return  general-purpose shader list
     */
    private static Shader[] createColorShaders( boolean isAllVisible ) {
        double c = isAllVisible ? 1 : 0;
        return new Shader[] {
            clip( Shaders.LUT_MPL2INFERNO, c * 0.1, true ),
            clip( Shaders.LUT_MPL2MAGMA, c * 0.1, true ),
            clip( Shaders.LUT_MPL2PLASMA, c * 0.1, true ),
            clip( Shaders.LUT_MPL2VIRIDIS, c * 0.06, true ),
            clip( Shaders.LUT_CUBEHELIX, c * 0.2, true ),
            clip( Shaders.SRON_RAINBOW, 0, true ),
            clip( Shaders.LUT_RAINBOW, 0, true ),
            clip( Shaders.LUT_GLNEMO2, c * 0.03, true ),
            clip( Shaders.LUT_RAINBOW3, 0, true ),
            clip( Shaders.LUT_PASTEL, c * 0.06, true ),
            clip( Shaders.LUT_ACCENT, 0, true ),
            clip( Shaders.LUT_GNUPLOT, c * 0.1, true ),
            clip( Shaders.LUT_GNUPLOT2, c * 0.2, true ),
            clip( Shaders.LUT_SPECXB2Y, c * 0.1, true ),
            clip( Shaders.LUT_SET1, 0, true ),
            clip( Shaders.LUT_PAIRED, 0, true ),
            clip( Shaders.LUT_HOTCOLD, 0, false ),
            clip( Shaders.BREWER_RDBU, 0, false ),
            clip( Shaders.BREWER_PIYG, 0, false ),
            clip( Shaders.BREWER_BRBG, 0, false ),
            clip( Shaders.CYAN_MAGENTA, 0, false ),
            clip( Shaders.RED_BLUE, 0, false ),
            clip( Shaders.LUT_BRG, 0, true ),
            clip( Shaders.LUT_HEAT, c * 0.15, true ),
            clip( Shaders.LUT_COLD, c * 0.15, true ),
            clip( Shaders.LUT_LIGHT, c * 0.15, true ),
            clip( Shaders.WHITE_BLACK, c * 0.1, false ),
            clip( Shaders.LUT_COLOR, 0, true ),
            clip( Shaders.LUT_STANDARD, 0, true ),
            clip( Shaders.BREWER_BUGN, c * 0.15, false ),
            clip( Shaders.BREWER_BUPU, c * 0.15, false ),
            clip( Shaders.BREWER_ORRD, c * 0.15, false ),
            clip( Shaders.BREWER_PUBU, c * 0.15, false ),
            clip( Shaders.BREWER_PURD, c * 0.15, false ),
            clip( Shaders.HCL_POLAR, 0, false ),
            clip( Shaders.FIX_HUE, 0, false ),
            clip( Shaders.FIX_INTENSITY, 0, true ),
            clip( Shaders.FIX_RED, 0, false ),
            clip( Shaders.FIX_GREEN, 0, false ),
            clip( Shaders.FIX_BLUE, 0, false ),
            clip( Shaders.HSV_H, 0, false ),
            clip( Shaders.HSV_S, 0, false ),
            clip( Shaders.HSV_V, 0, true ),
            clip( Shaders.FIX_Y, 0, true ),
            clip( Shaders.FIX_U, 0, false ),
            clip( Shaders.FIX_V, 0, false ),
            clip( Shaders.SCALE_S, 0, false ),
            clip( Shaders.SCALE_V, 0, true ),
            clip( Shaders.SCALE_Y, 0, true ),
        };
    }

    /**
     * Adjusts a shader implementation to taste.
     * The output value has the same name as the input one,
     * even if it has been adjusted.
     *
     * @param  shader  base instance
     * @param  clip  fraction of the base range amount to exclude
     *               at the (output) low end
     * @param  flip  true iff the sense of the input shader is to be inverted
     * @return  output shader
     */
    private static Shader clip( Shader shader, double clip, boolean flip ) {
        String name = shader.getName();
        final float f0;
        final float f1;
        if ( flip ) {
            f0 = 1f - (float) clip;
            f1 = 0f;
        }
        else {
            f0 = (float) clip;
            f1 = 1f;
        }
        if ( f0 == 1f && f1 == 0f ) {
            shader = Shaders.invert( shader );
        }
        else if ( f0 != 0f || f1 != 1f ) {
            shader = Shaders.stretch( shader, f0, f1 );
        }
        if ( ! name.equals( shader.getName() ) ) {
            shader = Shaders.rename( shader, name );
        }
        return shader;
    }
}
