package uk.ac.starlink.topcat.plot2;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import uk.ac.starlink.table.DefaultValueInfo;
import uk.ac.starlink.table.DescribedValue;
import uk.ac.starlink.table.MetaCopyStarTable;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.TableBuilder;
import uk.ac.starlink.table.Tables;
import uk.ac.starlink.table.ValueInfo;
import uk.ac.starlink.topcat.BooleanColumnRowSubset;
import uk.ac.starlink.topcat.InverseRowSubset;
import uk.ac.starlink.topcat.RowSubset;
import uk.ac.starlink.topcat.SyntheticRowSubset;
import uk.ac.starlink.topcat.TopcatModel;
import uk.ac.starlink.ttools.plot2.PlotLayer;
import uk.ac.starlink.ttools.plot2.Plotter;
import uk.ac.starlink.ttools.plot2.config.ConfigMap;
import uk.ac.starlink.ttools.plot2.task.LayerSpec;
import uk.ac.starlink.ttools.task.Credibility;
import uk.ac.starlink.ttools.task.CredibleString;
import uk.ac.starlink.ttools.task.TableNamer;
import uk.ac.starlink.util.URLUtils;

/**
 * Aggregates a PlotLayer and some additional
 * information about how it was configured.
 * The resulting object is able to come up with a suitable LayerSpec.
 *
 * @author   Mark Taylor
 * @since    14 Jul 2017
 */
public class TopcatLayer {

    private final PlotLayer plotLayer_;
    private final ConfigMap config_;
    private final String leglabel_;
    private final TopcatModel tcModel_;
    private final GuiCoordContent[] contents_;
    private final RowSubset rset_;
    public static final TopcatNamer PATHNAME_NAMER;
    public static final TopcatNamer FILENAME_NAMER;
    public static final TopcatNamer LABEL_NAMER;
    public static final TopcatNamer TNUM_NAMER;
    private static final TableNamer[] TABLENAMERS = new TableNamer[] {
        PATHNAME_NAMER = new TopcatNamer( "Pathname", true ),
        FILENAME_NAMER = new TopcatNamer( "Filename", true ),
        LABEL_NAMER = new TopcatNamer( "Label", true ),
        TNUM_NAMER = new TopcatNamer( "TNum", false ),
    };
    private static final ValueInfo FORMAT_INFO =
        new DefaultValueInfo( TopcatLayer.class.getName() + "TableBuilder",
                              TableBuilder.class );

    /**
     * Constructs a layer based on a table.
     *
     * @param  plotLayer  plot layer, not null
     * @param  config   configuration used to set up the plot layer
     *                  (superset is permitted)
     * @param  leglabel  label used in the legend;
     *                   if null, excluded from the legend
     * @param  tcModel   TopcatModel containing the table
     * @param  contents  information about data columns used to construct plot
     *                   (superset is not permitted)
     * @param  rset    row subset for which layer is plotted
     */
    public TopcatLayer( PlotLayer plotLayer, ConfigMap config, String leglabel,
                        TopcatModel tcModel, GuiCoordContent[] contents,
                        RowSubset rset ) {
        plotLayer_ = plotLayer;
        config_ = config;
        leglabel_ = leglabel;
        tcModel_ = tcModel;
        contents_ = contents == null ? new GuiCoordContent[ 0 ] : contents;
        rset_ = rset;
    }

    /**
     * Constructs a layer with no table data.
     *
     * @param  plotLayer  plot layer, not null
     * @param  config   configuration used to set up the plot layer
     *                  (superset is permitted)
     * @param  leglabel  label used in the legend;
     *                   if null, excluded from the legend
     */
    public TopcatLayer( PlotLayer plotLayer, ConfigMap config,
                        String leglabel ) {
        this( plotLayer, config, leglabel, null, null, null );
    }

    /**
     * Returns this object's plot layer.
     *
     * @return  plot layer, not null
     */
    public PlotLayer getPlotLayer() {
        return plotLayer_;
    }

    /**
     * Returns a layer specification for this layer placed within
     * a given zone.
     *
     * <p>It shouldn't be null, unless it was impossible to write the
     * specification for some reason??
     *
     * @param   izone   zone index for created layer
     * @return  layer specification, hopefully not null??
     */
    public LayerSpec getLayerSpec( int izone ) {
        Plotter plotter = plotLayer_.getPlotter();
        if ( tcModel_ == null ) {
            return new LayerSpec( plotter, config_, leglabel_, izone );
        }
        else {
            Map<String,String> coordMap =
                GuiCoordContent.getInputValues( contents_ );
            CredibleString selectExpr = getSelectExpression( rset_ );
            StarTable table = getLayerTable( tcModel_ );
            return new LayerSpec( plotter, config_, leglabel_, izone,
                                  table, coordMap, selectExpr );
        }
    }

    /**
     * Returns a list of TableNamer objects that give the user options for
     * referencing TopcatModels by a text string in generated stilts commands.
     * The stilts commands are assumed to have been specified using
     * methods in this class.
     *
     * @return  table namer user options
     */
    public static TableNamer[] getLayerTableNamers() {
        return TABLENAMERS;
    }

    /**
     * Returns a best effort at an expression indicating row selection
     * corresponding to a given RowSubset.
     * In some cases, for instance a subset defined by a bitmap,
     * there's no way to do this that will result in an evaluatable
     * expression, so in those cases just return the subset name or something.
     *
     * @param  rset  row subset
     * @return   attempt at expression giving row inclusion, not null
     */
    private static CredibleString getSelectExpression( RowSubset rset ) {
        if ( rset == null ) {
            return null;
        }
        else if ( rset.equals( RowSubset.ALL ) ) {
            return null;
        }
        else if ( rset.equals( RowSubset.NONE ) ) {
            return new CredibleString( "false", Credibility.YES );
        }
        if ( rset instanceof SyntheticRowSubset ) {
            return new CredibleString( ((SyntheticRowSubset) rset)
                                       .getExpression(), Credibility.MAYBE );
        }
        else if ( rset instanceof BooleanColumnRowSubset ) {
            BooleanColumnRowSubset cset = (BooleanColumnRowSubset) rset;
            String expr = cset.getTable()
                         .getColumnInfo( cset.getColumnIndex() ).getName();
            return new CredibleString( expr, Credibility.YES );
        }
        else if ( rset instanceof InverseRowSubset ) {
            CredibleString invResult =
                getSelectExpression( ((InverseRowSubset) rset)
                                    .getInvertedSubset() );
            return new CredibleString( "!(" + invResult.getValue() + ")",
                                       invResult.getCredibility() );
        }
        else {
            return new CredibleString( "<" + rset.getName() + ">",
                                       Credibility.NO );
        }
    }

    /**
     * Returns a table corresponding to the current apparent table of
     * a topcat model.  Its parameter list also contains parameters
     * giving various naming options corresponding to the FileNamer
     * instances defined by this class.
     *
     * @param   tcModel  topcat model
     * @return   table view
     */
    private static StarTable getLayerTable( TopcatModel tcModel ) {
        List<DescribedValue> params = new ArrayList<DescribedValue>();
        params.add( new DescribedValue( FORMAT_INFO,
                                        tcModel.getTableFormat() ) );
        params.add( TNUM_NAMER
                   .createNameParam( "T" + tcModel.getID(), Credibility.NO ) );
        params.add( LABEL_NAMER
                   .createNameParam( tcModel.getLabel(), Credibility.MAYBE ) );
        String loc = tcModel.getLocation();
        URL url;
        try {
            url = new URL( loc );
        }
        catch ( MalformedURLException e ) {
            url = null;
        }
        File file = url == null ? null
                                : URLUtils.urlToFile( url.toString() );
        if ( file == null ) {
            file = new File( loc );
        }
        final CredibleString filename;
        final CredibleString pathname;
        if ( url != null ) {
            filename = new CredibleString( file.getName(), Credibility.NO );
            pathname = new CredibleString( loc, Credibility.YES );
        }
        else if ( file.exists() ) {
            filename = new CredibleString( file.getName(), Credibility.MAYBE );
            pathname = new CredibleString( file.getAbsolutePath(),
                                           Credibility.YES );
        }
        else {
            filename = new CredibleString( loc, Credibility.NO );
            pathname = filename;
        }
        params.add( FILENAME_NAMER.createNameParam( filename ) );
        params.add( PATHNAME_NAMER.createNameParam( pathname ) );
        StarTable table =
            new MetaCopyStarTable( tcModel.getViewModel().getSnapshot() );
        table.getParameters().addAll( params );
        return table;
    }

    /**
     * TableNamer implementation for use by this class.
     * An instance of this class can be used prepare a DescribedValue
     * to be stashed in the Parameter list of a StarTable, where the
     * value is the name to be used for that table.
     */
    private static class TopcatNamer implements TableNamer {
        final String name_;
        final ValueInfo nameInfo_;
        final boolean hasFormat_;

        /**
         * Constructor.
         *
         * @param  name   TableNamer user name
         * @param  hasFormat   whether to report table format when available
         */
        TopcatNamer( String name, boolean hasFormat ) {
            name_ = name;
            hasFormat_ = hasFormat;
            String paramName = TopcatLayer.class.getName() + "_" + name;
            nameInfo_ = new DefaultValueInfo( paramName, CredibleString.class );
        }

        /**
         * Returns an object to be stashed in a table's parameter list
         * giving the table name.
         *
         * @param  credStr  value
         * @return  described value
         */
        DescribedValue createNameParam( CredibleString credStr ) {
            return new DescribedValue( nameInfo_, credStr );
        }

        /**
         * Returns an object to be stashed in a table's parameter list
         * giving the table name.
         *
         * @param  str   table name string
         * @param  cred  table name credibility
         * @return  described value
         */
        DescribedValue createNameParam( String str, Credibility cred ) {
            return createNameParam( new CredibleString( str, cred ) );
        }

        public CredibleString nameTable( StarTable table ) {
            Object value = Tables.getValue( table.getParameters(), nameInfo_ );
            return value instanceof CredibleString
                 ? (CredibleString) value
                 : new CredibleString( "???", Credibility.NO );
        }

        public TableBuilder getTableFormat( StarTable table ) {
            if ( hasFormat_ ) {
                Object fmt = Tables.getValue( table.getParameters(),
                                              FORMAT_INFO );
                return fmt instanceof TableBuilder
                     ? (TableBuilder) fmt
                     : null;
            }
            else {
                return null;
            }
        }

        @Override
        public String toString() {
            return name_;
        }
    }
}
