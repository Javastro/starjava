package uk.ac.starlink.table.formats;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.List;
import uk.ac.starlink.table.AbstractStarTable;
import uk.ac.starlink.table.ColumnInfo;
import uk.ac.starlink.table.ReaderRowSequence;
import uk.ac.starlink.table.RowSequence;
import uk.ac.starlink.table.TableFormatException;
import uk.ac.starlink.util.DataSource;

/**
 * Abstract superclass for tables which reads a stream of characters to
 * obtain the row data and metadata.
 * Since metadata is typically scarce in such tables, the strategy is
 * to make one pass through the data attempting to work out 
 * column types etc at table initialisation time, and to make 
 * a further pass through for each required RowSequence, using the
 * metadata obtained earlier.
 *
 * @author   Mark Taylor (Starlink)
 * @since    21 Sep 2004
 * @see   RowEvaluator
 */
public abstract class StreamStarTable extends AbstractStarTable {

    private final DataSource datsrc_;
    private final int ncol_;
    private final long nrow_;
    private final RowEvaluator.Decoder[] decoders_;
    private final ColumnInfo[] colInfos_;

    /** Char representation of -1 (as returned end-of-stream read) */
    protected final static char END = (char) -1;

    /**
     * Constructor.
     *
     * @param  datsrc  data source from which the stream can be obtained
     */
    protected StreamStarTable( DataSource datsrc )
            throws TableFormatException, IOException {
        datsrc_ = datsrc;

        /* Work out the table metadata, probably by reading through
         * the rows once. */
        RowEvaluator.Metadata meta = obtainMetadata();
        decoders_ = meta.decoders_;
        colInfos_ = meta.colInfos_;
        nrow_ = meta.nrow_;
        ncol_ = meta.ncol_;

        /* Configure some table characteristics from the data source. */
        setName( datsrc_.getName() );
        setURL( datsrc.getURL() );
    }

    public int getColumnCount() {
        return ncol_;
    }

    public long getRowCount() {
        return nrow_;
    }

    public ColumnInfo getColumnInfo( int icol ) {
        return colInfos_[ icol ];
    }

    public RowSequence getRowSequence() throws IOException {
        final PushbackInputStream in = getInputStream();
        final int ncol = getColumnCount();
        return new ReaderRowSequence() {
            protected Object[] readRow() throws IOException {
                List cellList = StreamStarTable.this.readRow( in );
                if ( cellList == null ) {
                    in.close();
                    return null;
                }
                else {
                    Object[] row = new Object[ ncol ];
                    for ( int icol = 0; icol < ncol; icol++ ) {
                        String sval = (String) cellList.get( icol );
                        if ( sval != null && sval.length() > 0 ) {
                            row[ icol ] = decoders_[ icol ].decode( sval );
                        }
                    }
                    return row;
                }
            }
            public void close() throws IOException {
                in.close();
            }
        };
    }

    /**
     * Convenience method which returns a buffered pushback stream based
     * on this table's data source.
     *
     * @return  input stream containing source data
     */
    protected PushbackInputStream getInputStream() throws IOException {
        return new PushbackInputStream( 
                   new BufferedInputStream( datsrc_.getInputStream() ) );
    }

    /**
     * Obtains column metadata for this table, probably by reading through
     * the rows once and using a RowEvaluator.  Note, this method is
     * called in the StreamStarTable constructor.
     *
     * @return   information about the table represented by the character
     *           stream
     * @throws   TableFormatException  if the data doesn't represent this
     *           kind of table
     * @throws   IOException   if I/O error is encountered
     */
    protected abstract RowEvaluator.Metadata obtainMetadata()
            throws TableFormatException, IOException;

    /**
     * Reads the next row of data from a given stream.
     * Ignorable rows are skipped; comments may be stashed away.
     *
     * @param  in  input stream
     * @return  list of Strings one for each cell in the row, or
     *          <tt>null</tt> for end of stream
     * @throws   TableFormatException  if the data doesn't represent this
     *           kind of table
     * @throws   IOException   if I/O error is encountered
     */
    protected abstract List readRow( PushbackInputStream in )
            throws TableFormatException, IOException;
}
