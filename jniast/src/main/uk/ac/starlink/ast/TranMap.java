/* ********************************************************
 * This file automatically generated by TranMap.pl.
 *                   Do not edit.                         *
 **********************************************************/

package uk.ac.starlink.ast;


/**
 * Java interface to the AST TranMap class
 *  - mapping with specified forward and inverse transformations. 
 * A TranMap is a Mapping which combines the forward transformation of
 * a supplied Mapping with the inverse transformation of another
 * supplied Mapping, ignoring the un-used transformation in each
 * Mapping (indeed the un-used transformation need not exist).
 * <p>
 * When the forward transformation of the TranMap is referred to, the
 * transformation actually used is the forward transformation of the
 * first Mapping supplied when the TranMap was constructed. Likewise, 
 * when the inverse transformation of the TranMap is referred to, the
 * transformation actually used is the inverse transformation of the
 * second Mapping supplied when the TranMap was constructed.
 * 
 * 
 * @see  <a href='http://star-www.rl.ac.uk/cgi-bin/htxserver/sun211.htx/?xref_TranMap'>AST TranMap</a>  
 */
public class TranMap extends Mapping {
    /** 
     * Creates a TranMap.   
     * @param  map1  Pointer to the first component Mapping, which defines the
     * forward transformation.
     * 
     * @param  map2  Pointer to the second component Mapping, which defines the
     * inverse transformation.
     * 
     * @throws  AstException  if an error occurred in the AST library
    */
    public TranMap( Mapping map1, Mapping map2 ) {
        construct( map1, map2 );
    }
    private native void construct( Mapping map1, Mapping map2 );

}
