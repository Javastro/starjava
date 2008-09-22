package uk.ac.starlink.datanode.nodes;

import java.io.IOException;
import javax.swing.JComponent;
import nom.tam.fits.AsciiTable;
import nom.tam.fits.AsciiTableHDU;
import nom.tam.fits.BinaryTable;
import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.Data;
import nom.tam.fits.FitsException;
import nom.tam.fits.TableData;
import nom.tam.fits.TableHDU;
import nom.tam.fits.Header;
import nom.tam.util.ArrayDataInput;
import uk.ac.starlink.fits.FitsTableBuilder;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.util.DataSource;

/**
 * An implementation of the {@link DataNode} interface for 
 * representing binary or ASCII tables in FITS HDUs.
 *
 * @author   Mark Taylor (Starlink)
 * @version  $Id$
 */
public class TableHDUDataNode extends HDUDataNode {

    private String hduType;
    private TableData tdata;
    private String description;
    private Header header;
    private FITSDataNode.ArrayDataMaker hdudata;
    private StarTable starTable;

    /**
     * Initialises a <code>TableHDUDataNode</code> from an <code>Header</code>
     * object.  The stream is read to the end of the HDU.
     *
     * @param   header a FITS header object from which the node is to be created
     * @param   hdudata  object which can supply the data stream where
     *          the data resides
     */
    public TableHDUDataNode( Header header, 
                             FITSDataNode.ArrayDataMaker hdudata ) 
            throws NoSuchDataException {
        super( header, hdudata );
        this.header = header;
        this.hdudata = hdudata;
        hduType = getHduType();
        String type;
        try {
            if ( BinaryTableHDU.isHeader( header ) ) {
                tdata = new BinaryTable( header );
                type = "Binary";
            }
            else if ( AsciiTableHDU.isHeader( header ) ) {
                tdata = new AsciiTable( header );
                type = "ASCII";
            }
            else {
                throw new NoSuchDataException( "Not a table" );
            }

            /* Get the column information from the header. */
            int ncols = header.getIntValue( "TFIELDS" );
            int nrows = header.getIntValue( "NAXIS2" );

            description = type + " table (" + ncols + "x" + nrows + ")";
        }
        catch ( FitsException e ) {
            throw new NoSuchDataException( e );
        }
        setIconID( IconFactory.TABLE );
    }

    /**
     * Returns the StarTable containing the data.  Its construction,
     * which involves reading from the stream, is deferred until 
     * necessary. 
     * 
     * @return   the StarTable object containing the data for this HDU
     */
    public synchronized StarTable getStarTable() throws IOException {

        /* If we haven't got it yet, read data and build a StarTable. */
        if ( starTable == null ) {
            try {
                ArrayDataInput istrm = hdudata.getArrayData();
                DataSource datsrc = hdudata.getDataSource();
                long offset = hdudata.getOffset();
                starTable = FitsTableBuilder
                           .attemptReadTable( istrm, true, datsrc,
                                              new long[] { offset } );
            }
            catch ( FitsException e ) {
                throw (IOException) new IOException( e.getMessage() ) 
                                   .initCause( e );
            }
        }
        return starTable;
    }

    public boolean isStarTable() {
        return true;
    }

    public boolean allowsChildren() {
        return false;
    }

    public String getDescription() {
        return description;
    }

    public String getNodeTLA() {
        return "TBL";
    }

    public String getNodeType() {
        return "FITS Table HDU";
    }

    public boolean hasDataObject( DataType dtype ) {
        if ( dtype == DataType.TABLE ) {
            return true;
        }
        else {
            return super.hasDataObject( dtype );
        }
    }

    public Object getDataObject( DataType dtype ) throws DataObjectException {
        if ( dtype == DataType.TABLE ) {
            try {
                return getStarTable();
            }
            catch ( IOException e ) {
                throw new DataObjectException( e );
            }
        }
        else {
            return super.getDataObject( dtype );
        }
    }

}