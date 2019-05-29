package uk.ac.starlink.ttools.taplint;

import org.xml.sax.Locator;
import uk.ac.starlink.ttools.votlint.SaxMessager;

/**
 * SaxMessager implementation which delivers its output via
 * a TapLint-style reporter.
 *
 * @author   Mark Taylor
 * @since    10 Jun 2011
 */
public class ReporterSaxMessager implements SaxMessager {

    private final Reporter reporter_;

    /**
     * Constructor.
     *
     * @param  reporter   validation message destination
     */
    public ReporterSaxMessager( Reporter reporter ) {
        reporter_ = reporter;
    }

    public void reportMessage( Level level, String msg, Locator locator ) {
        ReportType type = getReportType( level );
        String label = "VO"
                     + AdhocCode.createLabelChars( type + ": " + msg,
                                                   AdhocCode.LABEL_LENGTH - 2 );
        ReportCode code = new AdhocCode( type, label );
        int il = -1;
        int ic = -1;
        if ( locator != null ) {
            ic = locator.getColumnNumber();
            il = locator.getLineNumber();
        }
        StringBuffer sbuf = new StringBuffer();
        if ( il > 0 ) {
            sbuf.append( " (l." )
                .append( il );
            if ( ic > 0 ) {
                sbuf.append( ", c." )
                    .append( ic );
            }
            sbuf.append( ")" );
        }
        sbuf.append( ": " )
            .append( msg );
        reporter_.report( code, sbuf.toString() );
    }

    /**
     * Returns the ReportType corresponding to a given SaxMessager.Level.
     *
     * @param  level   level
     * @return  report type
     */
    private ReportType getReportType( Level level ) {
        switch ( level ) {
            case INFO:
                return ReportType.INFO;
            case WARNING:
                return ReportType.WARNING;
            case ERROR:
                return ReportType.ERROR;
            default:
                assert false;
                return ReportType.FAILURE;
        }
    }
}
