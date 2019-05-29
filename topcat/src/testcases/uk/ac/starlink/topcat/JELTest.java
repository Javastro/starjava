package uk.ac.starlink.topcat;

import gnu.jel.CompiledExpression;
import gnu.jel.CompilationException;
import gnu.jel.Evaluator;
import gnu.jel.Library;
import java.util.logging.Level;
import java.util.logging.Logger;
import uk.ac.starlink.table.ArrayColumn;
import uk.ac.starlink.table.ColumnStarTable;
import uk.ac.starlink.ttools.jel.RandomJELRowReader;
import uk.ac.starlink.util.TestCase;

public class JELTest extends TestCase {

    static {
        Logger.getLogger( "uk.ac.starlink.ast" ).setLevel( Level.OFF );
        Logger.getLogger( "uk.ac.starlink.util" ).setLevel( Level.OFF );
    }

    public JELTest( String name ) {
        super( name );
    }

    public void testLibrary() throws Throwable {
        ColumnStarTable st = ColumnStarTable.makeTableWithRows( 4 );
        st.addColumn( ArrayColumn
                     .makeColumn( "X", new int[] { 0, 1, 2, 3, } ) );
        st.addColumn( ArrayColumn
                     .makeColumn( "Y", new double[] { 0., 1., 4., 9. } ) );
        RandomJELRowReader jrr = new RandomJELRowReader( st );
        for ( int i = 0; i < 2; i++ ) {
            Library lib = TopcatJELUtils.getLibrary( jrr, i > 0 );
            CompiledExpression compex =
                Evaluator.compile( "X+$2", lib, double.class );
            for ( int j = 0; j < 3; j++ ) {
                double result = j + j * j;
                assertEquals( result, 
                              ( (Double) jrr.evaluateAtRow( compex, j ) )
                             .doubleValue() );
                jrr.setCurrentRow( j );
                assertEquals( result,
                              ( (Double) jrr.evaluate( compex ) )
                             .doubleValue() );
            }
            try {
                Evaluator.compile( "tits", lib, null );
                fail();
            }
            catch ( CompilationException e ) {
            }
        }
    }

    public void testIdentifier() {
        for ( String s : new String[] {
                  " ab", "a b", "hello.world", "if", "1+2", "-3", "99",
                  "false", "true", "null" } ) {
            assertFalse( TopcatJELUtils.isJelIdentifier( s ) );
        }
        for ( String s : new String[] {
                  "ab", "helloIamAVariable", "____", "$23001", "x", "xy", "x_y",
                  "vfmnx", "gg99", "$index", "$00", } ) {
            assertTrue( TopcatJELUtils.isJelIdentifier( s ) );
        }
    }
}
