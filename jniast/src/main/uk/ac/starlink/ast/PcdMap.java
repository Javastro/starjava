/* ********************************************************
 * This file automatically generated by PcdMap.pl.
 *                   Do not edit.                         *
 **********************************************************/

package uk.ac.starlink.ast;


/**
 * Java interface to the AST PcdMap class
 *  - apply 2-dimensional pincushion/barrel distortion. 
 * A PcdMap is a non-linear Mapping which transforms 2-dimensional
 * positions to correct for the radial distortion introduced by some
 * cameras and telescopes. This can take the form either of pincushion
 * or barrel distortion, and is characterized by a single distortion
 * coefficient.
 * <p>
 * A PcdMap is specified by giving this distortion coefficient and the
 * coordinates of the centre of the radial distortion. The forward
 * transformation of a PcdMap applies the distortion:
 * <p>
 *    RD = R * ( 1 + C * R * R )
 * <p>
 * where R is the undistorted radial distance from the distortion 
 * centre (specified by attribute PcdCen), RD is the radial distance 
 * from the same centre in the presence of distortion, and C is the 
 * distortion coefficient (given by attribute Disco).
 * <p>
 * The inverse transformation of a PcdMap removes the distortion
 * produced by the forward transformation. The expression used to derive 
 * R from RD is an approximate inverse of the expression above.
 * 
 * 
 * @see  <a href='http://star-www.rl.ac.uk/cgi-bin/htxserver/sun211.htx/?xref_PcdMap'>AST PcdMap</a>  
 */
public class PcdMap extends Mapping {
    /** 
     * Creates a PcdMap.   
     * @param  disco  The distortion coefficient. Negative values give barrel
     * distortion, positive values give pincushion distortion, and 
     * zero gives no distortion.
     * 
     * @param  pcdcen  A 2-element array containing the coordinates of the centre of the
     * distortion.
     * 
     * @throws  AstException  if an error occurred in the AST library
    */
    public PcdMap( double disco, double[] pcdcen ) {
        construct( disco, pcdcen );
    }
    private native void construct( double disco, double[] pcdcen );

    /**
     * Get 
     * pcdMap pincushion/barrel distortion coefficient.  
     * This attribute specifies the pincushion/barrel distortion coefficient
     * used by a PcdMap. This coefficient is set when the PcdMap is created,
     * but may later be modified. If the attribute is cleared, its default
     * value is zero, which gives no distortion. For pincushion distortion,
     * the value should be positive. For barrel distortion, it should be
     * negative.
     * <p>
     * Note that the forward transformation of a PcdMap applies the 
     * distortion specified by this attribute and the inverse 
     * transformation removes this distortion. If the PcdMap is inverted 
     * (e.g. using astInvert), then the forward transformation will 
     * remove the distortion and the inverse transformation will apply
     * it. The distortion itself will still be given by the same value of
     * Disco.
     * 
     *
     * @return  this object's Disco attribute
     */
    public double getDisco() {
        return getD( "Disco" );
    }

    /**
     * Set 
     * pcdMap pincushion/barrel distortion coefficient.  
     * This attribute specifies the pincushion/barrel distortion coefficient
     * used by a PcdMap. This coefficient is set when the PcdMap is created,
     * but may later be modified. If the attribute is cleared, its default
     * value is zero, which gives no distortion. For pincushion distortion,
     * the value should be positive. For barrel distortion, it should be
     * negative.
     * <p>
     * Note that the forward transformation of a PcdMap applies the 
     * distortion specified by this attribute and the inverse 
     * transformation removes this distortion. If the PcdMap is inverted 
     * (e.g. using astInvert), then the forward transformation will 
     * remove the distortion and the inverse transformation will apply
     * it. The distortion itself will still be given by the same value of
     * Disco.
     * 
     *
     * @param  disco   the Disco attribute of this object
     */
    public void setDisco( double disco ) {
       setD( "Disco", disco );
    }

    /**
     * Get 
     * centre coordinates of pincushion/barrel distortion by axis. 
     * This attribute specifies the centre of the pincushion/barrel
     * distortion implemented by a PcdMap. It takes a separate value for
     * each axis of the PcdMap so that, for instance, the settings
     * "PcdCen(1)=345.0,PcdCen(2)=-104.4" specify that the pincushion
     * distortion is centred at positions of 345.0 and -104.4 on axes 1 and 2
     * respectively. This attribute is set when a PcdMap is created, but may
     * later be modified. If the attribute is cleared, the default value for
     * both axes is zero.
     * <h4>Notes</h4>
     * <br> - If no axis is specified, (e.g. "PcdCen" instead of 
     * "PcdCen(2)"), then a "set" or "clear" operation will affect 
     * the attribute value of both axes, while a "get" or "test" 
     * operation will use just the PcdCen(1) value. 
     * @param   axis  the index of the axis to get the value for (1 or 2)
     * @return        the PcdCen attribute for the indicated axis of this
     *                mapping
     * @throws  IndexOutOfBoundsException  if <code>axis</code> is not in
     *                                     the range 1..2.
     */
    public double getPcdCen( int axis ) {
        if ( axis >= 1 && axis <= 2 ) {
            return getD( "PcdCen" + "(" + axis + ")" );
        }
        else {
            throw new IndexOutOfBoundsException(
               "axis value " + axis + " is not in the range 1..2" );
        }
    }

    /**
     * Set 
     * centre coordinates of pincushion/barrel distortion by axis. 
     * This attribute specifies the centre of the pincushion/barrel
     * distortion implemented by a PcdMap. It takes a separate value for
     * each axis of the PcdMap so that, for instance, the settings
     * "PcdCen(1)=345.0,PcdCen(2)=-104.4" specify that the pincushion
     * distortion is centred at positions of 345.0 and -104.4 on axes 1 and 2
     * respectively. This attribute is set when a PcdMap is created, but may
     * later be modified. If the attribute is cleared, the default value for
     * both axes is zero.
     * <h4>Notes</h4>
     * <br> - If no axis is specified, (e.g. "PcdCen" instead of 
     * "PcdCen(2)"), then a "set" or "clear" operation will affect 
     * the attribute value of both axes, while a "get" or "test" 
     * operation will use just the PcdCen(1) value.
     * @param   axis  the index of the axis to set the value for (1 or 2)
     * @param   pcdCen  the PcdCen attribute for the indicated axis of
     *                  this mapping
     * @throws  IndexOutOfBoundsException  if <code>axis</code> is not in
     *                                     the range 1..2.
     */
    public void setPcdCen( int axis, double pcdCen ) {
        if ( axis >= 1 && axis <= 2 ) {
            setD( "PcdCen" + "(" + axis + ")", pcdCen );
        }
        else {
            throw new IndexOutOfBoundsException(
               "axis value " + axis + " is not in the range 1..2" );
        }
    }

}
