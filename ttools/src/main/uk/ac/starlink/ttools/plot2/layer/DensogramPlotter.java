package uk.ac.starlink.ttools.plot2.layer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Icon;
import uk.ac.starlink.ttools.gui.ResourceIcon;
import uk.ac.starlink.ttools.plot.Range;
import uk.ac.starlink.ttools.plot.Shader;
import uk.ac.starlink.ttools.plot.Style;
import uk.ac.starlink.ttools.plot2.Axis;
import uk.ac.starlink.ttools.plot2.LayerOpt;
import uk.ac.starlink.ttools.plot2.PlotUtil;
import uk.ac.starlink.ttools.plot2.ReportMap;
import uk.ac.starlink.ttools.plot2.Scaler;
import uk.ac.starlink.ttools.plot2.Scaling;
import uk.ac.starlink.ttools.plot2.config.ConfigKey;
import uk.ac.starlink.ttools.plot2.config.ConfigMap;
import uk.ac.starlink.ttools.plot2.config.ConfigMeta;
import uk.ac.starlink.ttools.plot2.config.DoubleConfigKey;
import uk.ac.starlink.ttools.plot2.config.IntegerConfigKey;
import uk.ac.starlink.ttools.plot2.config.RampKeySet;
import uk.ac.starlink.ttools.plot2.config.StyleKeys;
import uk.ac.starlink.ttools.plot2.data.DataSpec;
import uk.ac.starlink.ttools.plot2.data.DataStore;
import uk.ac.starlink.ttools.plot2.data.FloatingCoord;
import uk.ac.starlink.ttools.plot2.geom.PlaneSurface;

/**
 * Plots a histogram-like density map - a one-dimensional colour bar
 * indicating density on the horizontal axis.
 *
 * @author   Mark Taylor
 * @since    20 Feb 2015
 */
public class DensogramPlotter
        extends Pixel1dPlotter<DensogramPlotter.DensoStyle> {

    /** Config keyset for the colour ramp. */
    public static final RampKeySet RAMP_KEYSET =
        new RampKeySet( "dense", "Density", StyleKeys.createAuxShaders(),
                        Scaling.LINEAR, true );

    /** Config key for the height of the density bar. */
    public static final ConfigKey<Integer> EXTENT_KEY =
        IntegerConfigKey.createSliderKey(
            new ConfigMeta( "size", "Size" )
           .setStringUsage( "<pixels>" )
           .setShortDescription( "Height in pixels of the density bar" )
           .setXmlDescription( new String[] {
                "<p>Height of the density bar in pixels.",
                "</p>",
            } )
        , 12, 1, 100, false );

    /** Config key for the position of the density bar. */
    public static final ConfigKey<Double> POSITION_KEY =
        DoubleConfigKey.createSliderKey(
            new ConfigMeta( "pos", "Position" )
           .setStringUsage( "<fraction>" )
           .setShortDescription( "Location on plot of density bar,"
                               + " in range 0..1" )
           .setXmlDescription( new String[] {
                "<p>Determines where on the plot region the density bar",
                "appears.",
                "The value should be in the range 0..1;",
                "zero corresponds to the bottom of the plot",
                "and one to the top.",
                "</p>",
            } )
        , 0.05, 0, 1, false );

    /**
     * Constructor.
     *
     * @param   xCoord  X axis coordinate
     * @param   hasWeight   true to permit histogram weighting
     */
    public DensogramPlotter( FloatingCoord xCoord, boolean hasWeight ) {
        super( xCoord, hasWeight, "Densogram", ResourceIcon.PLOT_DENSOGRAM );
    }

    public String getPlotterDescription() {
        return PlotUtil.concatLines( new String[] {
            "<p>Represents smoothed density of data values",
            "along the horizontal axis using a colourmap.",
            "This is like a",
            "<ref id='layer-kde'>Kernel Density Estimate</ref>",
            "(smoothed histogram with bins 1 pixel wide),",
            "but instead of representing the data extent vertically",
            "as bars or a line,",
            "values are represented by a fixed-size pixel-width column",
            "of a colour from a colour map.",
            "A smoothing kernel, whose width and shape may be varied,",
            "is applied to each data point.",
            "</p>",
            "<p>This is a rather unconventional way to represent density data,",
            "and this plotting mode is probably not very useful.",
            "But hey, nobody's forcing you to use it.",
            "</p>",
        } );
    }

    public ConfigKey[] getStyleKeys() {
        List<ConfigKey> list = new ArrayList<ConfigKey>();
        list.add( StyleKeys.COLOR );
        list.add( SMOOTH_KEY );
        list.add( KERNEL_KEY );
        list.addAll( Arrays.asList( RAMP_KEYSET.getKeys() ) );
        list.add( StyleKeys.CUMULATIVE );
        list.add( EXTENT_KEY );
        list.add( POSITION_KEY );
        return list.toArray( new ConfigKey[ 0 ] );
    }

    public DensoStyle createStyle( ConfigMap config ) {
        Color baseColor = config.get( StyleKeys.COLOR );
        RampKeySet.Ramp ramp = RAMP_KEYSET.createValue( config );
        double width = config.get( SMOOTH_KEY );
        Kernel1dShape kernelShape = config.get( KERNEL_KEY );
        Kernel1d kernel = kernelShape.createKernel( width );
        boolean cumul = config.get( StyleKeys.CUMULATIVE );
        int extent = config.get( EXTENT_KEY );
        double position = config.get( POSITION_KEY );
        return new DensoStyle( baseColor, ramp.getShader(), ramp.getScaling(),
                               kernel, cumul, extent, position );
    }

    protected void paintBins( PlaneSurface surface, BinArray binArray,
                              DensoStyle style, Graphics2D g ) {

        /* Get the data values for each pixel position. */
        Axis xAxis = surface.getAxes()[ 0 ];
        double[] bins = getDataBins( binArray, xAxis, style.kernel_,
                                     Normalisation.NONE, style.cumul_ );

        /* Work out the Y axis bounds. */
        Rectangle bounds = surface.getPlotBounds();
        int gy0 = bounds.y
                + (int) ( ( bounds.height - style.extent_ ) * 
                          ( 1.0 - style.position_ ) );

        /* Work out the range of bin indices that need to be painted. */
        int ixlo = binArray.getBinIndex( bounds.x );
        int ixhi = binArray.getBinIndex( bounds.x + bounds.width );
        int np = ixhi - ixlo;

        /* Get range. */
        double ymin = 0;
        double ymax = 0;
        for ( int ip = 0; ip < np; ip++ ) {
            int ix = ixlo + ip;
            double dy = bins[ ix ];
            ymin = Math.min( ymin, dy );
            ymax = Math.max( ymax, dy );
        }

        /* Do the painting. */
        Scaler scaler = style.scaling_.createScaler( ymin, ymax );
        float[] baseRgba = style.baseColor_.getRGBComponents( null );
        float[] rgba = new float[ 4 ];
        Color color0 = g.getColor();
        boolean isLog = style.scaling_.isLogLike();
        for ( int ip = 0; ip < np; ip++ ) {
            int ix = ixlo + ip;
            int gx = binArray.getGraphicsCoord( ix );
            double dy = bins[ ix ];
            if ( dy != 0 ) {
                double sy = scaler.scaleValue( dy );
                if ( isLog && sy < 0 ) {
                    sy = 0;
                }
                System.arraycopy( baseRgba, 0, rgba, 0, 4 );
                style.shader_.adjustRgba( rgba, (float) sy );
                Color color =
                    new Color( rgba[ 0 ], rgba[ 1 ], rgba[ 2 ], rgba[ 3 ] );
                g.setColor( color );
                g.fillRect( gx, gy0, 1, style.extent_ );
            }
        }
        g.setColor( color0 );
    }

    protected LayerOpt getLayerOpt( DensoStyle style ) {
        return LayerOpt.OPAQUE;
    }

    protected int getPixelPadding( DensoStyle style ) {
        return getEffectiveExtent( style.kernel_ );
    }

    protected void extendPixel1dCoordinateRanges( Range[] ranges,
                                                  boolean[] logFlags,
                                                  DensoStyle style,
                                                  DataSpec dataSpec,
                                                  DataStore dataStore ) {
        // no-op
    }

    protected ReportMap getPixel1dReport( Pixel1dPlan plan, DensoStyle style ) {
        return null;
    }

    /**
     * Plotting style for this class.
     */
    public static class DensoStyle implements Style {

        final Color baseColor_;
        final Shader shader_;
        final Scaling scaling_;
        final Kernel1d kernel_;
        final boolean cumul_;
        final int extent_;
        final double position_;

        /**
         * Constructor.
         *
         * @param  baseColor   base colour
         * @param  shader    colour ramp shader
         * @param  scaling   colour ramp scaling function
         * @param  kernel  smoothing kernel
         * @param  cumul  are bins painted cumulatively
         * @param  extent   height in pixels of density bar
         * @param  position   fractional location of density bar (0..1)
         */
        public DensoStyle( Color baseColor, Shader shader, Scaling scaling,
                           Kernel1d kernel, boolean cumul,
                           int extent, double position ) {
            baseColor_ = baseColor;
            shader_ = shader;
            scaling_ = scaling;
            kernel_ = kernel;
            cumul_ = cumul;
            extent_ = extent;
            position_ = position;
        }

        public Icon getLegendIcon() {
            return shader_.createIcon( true, 10, 8, 1, 2 );
        }

        @Override
        public int hashCode() {
            int code = 3455;
            code = 23 * code + baseColor_.hashCode();
            code = 23 * code + shader_.hashCode();
            code = 23 * code + scaling_.hashCode();
            code = 23 * code + kernel_.hashCode();
            code = 23 * code + ( cumul_ ? 13 : 17 );
            code = 23 * code + extent_;
            code = 23 * code + Float.floatToIntBits( (float) position_ );
            return code;
        }

        @Override
        public boolean equals( Object o ) {
            if ( o instanceof DensoStyle ) {
                DensoStyle other = (DensoStyle) o;
                return this.baseColor_.equals( other.baseColor_ )
                    && this.shader_.equals( other.shader_ )
                    && this.scaling_.equals( other.scaling_ )
                    && this.kernel_.equals( other.kernel_ )
                    && this.cumul_ == other.cumul_
                    && this.extent_ == other.extent_
                    && this.position_ == other.position_;
            }
            else {
                return false;
            }
        }
    }
}