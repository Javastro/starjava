package uk.ac.starlink.topcat.plot2;

import java.awt.Color;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import uk.ac.starlink.topcat.LineBox;
import uk.ac.starlink.topcat.ResourceIcon;
import uk.ac.starlink.topcat.ToggleButtonModel;
import uk.ac.starlink.ttools.plot.Range;
import uk.ac.starlink.ttools.plot.Shader;
import uk.ac.starlink.ttools.plot2.AuxScale;
import uk.ac.starlink.ttools.plot2.Captioner;
import uk.ac.starlink.ttools.plot2.PlotLayer;
import uk.ac.starlink.ttools.plot2.PlotUtil;
import uk.ac.starlink.ttools.plot2.ShadeAxis;
import uk.ac.starlink.ttools.plot2.ShadeAxisFactory;
import uk.ac.starlink.ttools.plot2.Subrange;
import uk.ac.starlink.ttools.plot2.config.BooleanConfigKey;
import uk.ac.starlink.ttools.plot2.config.ConfigException;
import uk.ac.starlink.ttools.plot2.config.ConfigKey;
import uk.ac.starlink.ttools.plot2.config.ConfigMap;
import uk.ac.starlink.ttools.plot2.config.ConfigMeta;
import uk.ac.starlink.ttools.plot2.config.RampKeySet;
import uk.ac.starlink.ttools.plot2.config.StyleKeys;
import uk.ac.starlink.ttools.plot2.config.StringConfigKey;

/**
 * Control for configuring shader scale and axis characteristics.
 *
 * @author   Mark Taylor
 * @since    13 Mar 2013
 */
public class ShaderControl extends ConfigControl {

    private final MultiConfigger configger_;
    private final AutoSpecifier<String> labelSpecifier_;
    private final AutoSpecifier<Boolean> visibleSpecifier_;
    private final ConfigSpecifier rangeSpecifier_;
    private static final AuxScale SCALE = AuxScale.COLOR;
    private static final RampKeySet RAMP_KEYS = StyleKeys.AUX_RAMP;
    private static final int RAMP_WIDTH = 15;
    private static final ConfigKey<String> AUXLABEL_KEY =
        new StringConfigKey( new ConfigMeta( "auxlabel", "Aux Axis Label" ),
                             null );
    private static final ConfigKey<Boolean> AUXVISIBLE_KEY =
        new BooleanConfigKey( new ConfigMeta( "auxaxis", "Show Scale" ),
                              false );

    /**
     * Constructor.
     *
     * @param   configger   config source containing some plot-wide config,
     *                      specifically captioner style
     * @param   auxLockModel   toggle to control whether aux ranges are
     *                         updated dynamically or held fixed;
     *                         may be null
     */
    public ShaderControl( MultiConfigger configger,
                          final ToggleButtonModel auxLockModel ) {
        super( SCALE.getName() + " Axis", ResourceIcon.COLORS );
        configger_ = configger;
        ActionListener forwarder = getActionForwarder();

        AutoConfigSpecifier axisSpecifier = new AutoConfigSpecifier(
            new ConfigKey[] { AUXVISIBLE_KEY, AUXLABEL_KEY,
                              StyleKeys.AUX_CROWD },
            new ConfigKey[] { AUXVISIBLE_KEY, AUXLABEL_KEY, }
        );
        labelSpecifier_ = axisSpecifier.getAutoSpecifier( AUXLABEL_KEY );
        visibleSpecifier_ = axisSpecifier.getAutoSpecifier( AUXVISIBLE_KEY );
        labelSpecifier_.setAutoValue( null );
        visibleSpecifier_.setAutoValue( false );
        configureForLayers( new LayerControl[ 0 ] );
        rangeSpecifier_ = new ConfigSpecifier( new ConfigKey[] {
            StyleKeys.SHADE_LOW, StyleKeys.SHADE_HIGH, StyleKeys.SHADE_SUBRANGE,
        } ) {
            @Override
            protected void checkConfig( ConfigMap config )
                    throws ConfigException {
                checkRangeSense( config, "Aux",
                                 StyleKeys.SHADE_LOW, StyleKeys.SHADE_HIGH );
            }
            @Override
            public JComponent createComponent() {
                JComponent box = Box.createVerticalBox();
                box.add( super.createComponent() );
                if ( auxLockModel != null ) {
                    JCheckBox lockBox = auxLockModel.createCheckBox();
                    lockBox.setHorizontalTextPosition( SwingConstants.LEADING );
                    box.add( new LineBox( lockBox ) );
                }
                return box;
            }
        };
        rangeSpecifier_.addActionListener( forwarder );

        ConfigKey[] shaderKeys =
            PlotUtil.arrayConcat( RAMP_KEYS.getKeys(),
                                  new ConfigKey[] { StyleKeys.AUX_NULLCOLOR } );
        addSpecifierTab( "Map", new ConfigSpecifier( shaderKeys ) );
        addSpecifierTab( "Ramp", axisSpecifier );
        addSpecifierTab( "Range", rangeSpecifier_ );
    }

    /**
     * Returns an aux value range explicitly fixed by the user.
     *
     * @return   shader fixed range, either or both bounds may be absent
     */
    public Range getFixRange() {
        ConfigMap rangeConfig = rangeSpecifier_.getSpecifiedValue();
        return new Range( PlotUtil
                         .toDouble( rangeConfig.get( StyleKeys.SHADE_LOW ) ),
                          PlotUtil
                         .toDouble( rangeConfig.get( StyleKeys.SHADE_HIGH ) ) );
    }

    /**
     * Returns an aux value subrange set by the user.
     *
     * @return   shader subrange
     */
    public Subrange getSubrange() {
        return rangeSpecifier_.getSpecifiedValue()
                              .get( StyleKeys.SHADE_SUBRANGE );
    }

    /**
     * Returns an object which can turn a range into a ShadeAxis
     * based on current config of this component and a set of layer controls.
     *
     * @param  controls   list of layer controls to which the axis will apply
     * @param  zid     identifier for zone to which axis factory applies
     * @return   shade axis factory
     */
    public ShadeAxisFactory createShadeAxisFactory( LayerControl[] controls,
                                                    ZoneId zid ) {
        final ConfigMap config = getConfig();
        config.putAll( configger_.getZoneConfig( zid ) );
        PlotLayer[] layers = getScaleLayers( controls, SCALE );
        boolean autoVis = layers.length > 0;
        String autoLabel = PlotUtil.getScaleAxisLabel( layers, SCALE );
        boolean visible = visibleSpecifier_.isAuto()
                        ? autoVis
                        : config.get( AUXVISIBLE_KEY );
        String label = labelSpecifier_.isAuto()
                     ? autoLabel
                     : config.get( AUXLABEL_KEY );
        if ( ! visible ) {
            return new ShadeAxisFactory() {
                public ShadeAxis createShadeAxis( Range range ) {
                    return null;
                }
                public boolean isLog() {
                    return false;
                }
            };
        }
        double crowd = config.get( StyleKeys.AUX_CROWD ).doubleValue();
        Captioner captioner = StyleKeys.CAPTIONER.createValue( config );
        RampKeySet.Ramp ramp = RAMP_KEYS.createValue( config );
        return RampKeySet.createShadeAxisFactory( ramp, captioner, label,
                                                  crowd, RAMP_WIDTH );
    }

    public boolean isLog() {
        return RAMP_KEYS.createValue( getConfig() ).getScaling().isLogLike();
    }

    /**
     * Configures state according to the current state of the control stack.
     *
     * @param  layerControls   list of layer controls relevant to this shading
     */
    public void configureForLayers( LayerControl[] layerControls ) {
        PlotLayer[] layers = getScaleLayers( layerControls, SCALE );
        boolean isAuto = layers.length > 0;
        if ( visibleSpecifier_.getAutoValue() != isAuto ) {
            visibleSpecifier_.setAutoValue( isAuto );
        }
        String label = layers.length == 0
                     ? null
                     : PlotUtil.getScaleAxisLabel( layers, SCALE );
        if ( ! PlotUtil.equals( labelSpecifier_.getAutoValue(), label ) ) {
            labelSpecifier_.setAutoValue( label );
        }
    }

    /**
     * Given a list of layer controls, extracts and returns all the layers
     * they produce that have AuxReaders for a given AuxScale.
     *
     * @param  layerControls  layer controls
     * @param  scale   aux scale of interest
     * @return  relevant layers
     */
    private static PlotLayer[] getScaleLayers( LayerControl[] layerControls,
                                               AuxScale scale ) {
        List<PlotLayer> list = new ArrayList<PlotLayer>();
        for ( LayerControl lc : layerControls ) {
            for ( TopcatLayer tcLayer : lc.getLayers() ) {
                PlotLayer plotLayer = tcLayer.getPlotLayer();
                if ( plotLayer.getAuxRangers().containsKey( scale ) ) {
                    list.add( plotLayer );
                }
            }
        }
        return list.toArray( new PlotLayer[ 0 ] );
    }
}
