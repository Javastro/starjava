/* ********************************************************
 * This file automatically generated by PolyMap.pl.
 *                   Do not edit.                         *
 **********************************************************/

package uk.ac.starlink.ast;


/**
 * Java interface to the AST PolyMap class
 *  - map coordinates using polynomial functions. 
 * A PolyMap is a form of Mapping which performs a general polynomial
 * transformation.  Each output coordinate is a polynomial function of
 * all the input coordinates. The coefficients are specified separately 
 * for each output coordinate. The forward and inverse transformations
 * are defined independantly by separate sets of coefficients.
 * 
 * 
 * @see  <a href='http://star-www.rl.ac.uk/cgi-bin/htxserver/sun211.htx/?xref_PolyMap'>AST PolyMap</a>  
 */
public class PolyMap extends Mapping {
    /** 
     * Creates a PolyMap.   
     * @param  nin  The number of input coordinates.
     * 
     * @param  nout  The number of output coordinates.
     * 
     * @param  ncoeff_f  The number of non-zero coefficients necessary to define the
     * forward transformation of the PolyMap. If zero is supplied, the
     * forward transformation will be undefined.
     * 
     * @param  coeff_f  An array containing 
     * "ncoeff_f*( 2 + nin )" elements. Each group of "2 + nin" 
     * adjacent elements describe a single coefficient of the forward
     * transformation. Within each such group, the first element is the
     * coefficient value; the next element is the integer index of the
     * PolyMap output which uses the coefficient within its defining
     * polynomial (the first output has index 1); the remaining elements
     * of the group give the integer powers to use with each input
     * coordinate value (powers must not be negative, and floating
     * point values are rounded to the nearest integer).
     * If "ncoeff_f" is zero, a NULL pointer may be supplied for "coeff_f".
     * <p>
     * For instance, if the PolyMap has 3 inputs and 2 outputs, each group 
     * consisting of 5 elements, A groups such as "(1.2, 2.0, 1.0, 3.0, 0.0)"
     * describes a coefficient with value 1.2 which is used within the 
     * definition of output 2. The output value is incremented by the
     * product of the coefficient value, the value of input coordinate
     * 1 raised to the power 1, and the value of input coordinate 2 raised 
     * to the power 3. Input coordinate 3 is not used since its power is
     * specified as zero. As another example, the group "(-1.0, 1.0,
     * 0.0, 0.0, 0.0 )" describes adds a constant value -1.0 onto
     * output 1 (it is a constant value since the power for every input
     * axis is given as zero).
     * <p>
     * Each final output coordinate value is the sum of the "ncoeff_f" terms
     * described by the "ncoeff_f" groups within the supplied array.
     * 
     * @param  ncoeff_i  The number of non-zero coefficients necessary to define the
     * inverse transformation of the PolyMap. If zero is supplied, the
     * inverse transformation will be undefined.
     * 
     * @param  coeff_i  An array containing 
     * "ncoeff_i*( 2 + nout )" elements. Each group of "2 + nout" 
     * adjacent elements describe a single coefficient of the inverse 
     * transformation, using the same schame as "coeff_f",
     * except that "inputs" and "outputs" are transposed.
     * If "ncoeff_i" is zero, a NULL pointer may be supplied for "coeff_i".
     * 
     * @throws  AstException  if an error occurred in the AST library
    */
    public PolyMap( int nin, int nout, int ncoeff_f, double[] coeff_f, int ncoeff_i, double[] coeff_i ) {
        construct( nin, nout, ncoeff_f, coeff_f, ncoeff_i, coeff_i );
    }
    private native void construct( int nin, int nout, int ncoeff_f, double[] coeff_f, int ncoeff_i, double[] coeff_i );

}
