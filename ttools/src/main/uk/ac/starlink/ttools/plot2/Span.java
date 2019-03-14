package uk.ac.starlink.ttools.plot2;

/**
 * Characterises the extent and possibly the distribution of a dataset.
 *
 * @author   Mark Taylor
 * @since    14 Mar 2019
 */
@Equality
public interface Span {

    /**
     * Returns the lower bound of this span.
     * If no lower bound is known, this may be NaN.
     *
     * @return   lower bound, may be NaN
     */
    double getLow();

    /**
     * Returns the upper bound of this span.
     * If no upper bound is known, this may be NaN.
     *
     * @return  upper bound, may be NaN
     */
    double getHigh();

    /**
     * Returns a 2-element array giving definite lower and upper bounds
     * to be used for this span.  The upper bound will be strictly greater
     * than the lower bound.  Optionally, both bounds can be required to
     * be strictly greater than zero.
     * If insufficient data is available to determine such return values,
     * some reasonable defaults will be made up.
     *
     * @param  requirePositive  whether output bounds must be positive
     * @return   2-element array giving (lo,hi)
     */
    double[] getFiniteBounds( boolean requirePositive );

    /**
     * Returns a scaler that maps the value range known by this span
     * to the range 0..1 according to a given scaling.
     *
     * @param  scaling  scale function
     * @return  new scaler
     */
    Scaler createScaler( Scaling scaling );

    /**
     * Creates a new span based on this one with optionally
     * adjusted data bounds.
     *
     * @param   lo   lower bound of output span; if NaN has no effect
     * @param   hi   upper bound of output span; if NaN has no effect
     * @return  new span
     */
    Span limit( double lo, double hi );
}
