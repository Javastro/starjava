package uk.ac.starlink.topcat.plot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import uk.ac.starlink.table.Tables;
import uk.ac.starlink.topcat.BasicAction;
import uk.ac.starlink.topcat.ResourceIcon;
import uk.ac.starlink.topcat.ToggleButtonModel;
import uk.ac.starlink.topcat.TopcatEvent;
import uk.ac.starlink.topcat.TopcatListener;
import uk.ac.starlink.topcat.TopcatModel;

/**
 * Graphics window for viewing 3D scatter plots.
 *
 * @author   Mark Taylor
 * @since    22 Nov 2005
 */
public abstract class Plot3DWindow extends GraphicsWindow
                                   implements TopcatListener {

    private final Plot3D plot_;
    private final JComponent plotPanel_;
    private final ToggleButtonModel fogModel_;
    private final ToggleButtonModel antialiasModel_;
    private final ToggleButtonModel northModel_;
    private final BlobPanel blobPanel_;
    private final Action blobAction_;
    private final Action fromVisibleAction_;
    private final CountsLabel plotStatus_;
    private double[] rotation_;
    private boolean isRotating_;
    private double zoom_ = 1.0;

    private static final double[] INITIAL_ROTATION = 
        rotateXY( rotateXY( new double[] { 1, 0, 0, 0, 1, 0, 0, 0, -1 },
                            0.5, 0.5 * Math.PI ),
                  0, -0.1 * Math.PI );

    /**
     * Constructor.
     *
     * @param   viewName  name of the view window
     * @param   axisNames  array of labels by which each axis is known;
     *          the length of this array defines the dimensionality of the plot
     * @param   naux  number of auxiliary axes
     * @param   parent   parent window - may be used for positioning
     * @param   errorModeModels   array of selecction models for error modes
     * @param   plot   the Plot3D object on which plotting is done
     */
    public Plot3DWindow( String viewName, String[] axisNames, int naux,
                         Component parent,
                         ErrorModeSelectionModel[] errorModeModels,
                         Plot3D plot ) {
        super( viewName, axisNames, naux, errorModeModels, parent );
        plot_ = plot;

        /* Set a suitable border on the plot.  The left part of this is
         * where the central zoom target live.  There's a zoom target
         * on the right as well (since this is where it was in 
         * previous versions) but it's very thin - this is mainly padding
         * between the plot and the legend region.  The top and bottom 
         * parts sometimes serve as overspills for auxiliary axis text. */
        plot_.setBorder( BorderFactory.createEmptyBorder( 10, 32, 10, 10 ) );

        /* Configure the plot to provide some callbacks when interesting
         * things happen. */
        plot_.setCallbacks( new Plot3D.Callbacks() {
            public void reportCounts( int nPoint, int nInc, int nVis ) {
                plotStatus_.setValues( new int[] { nPoint, nInc, nVis } );
            }
            public void requestZoom( double zoom ) {
                zoom_ = Math.max( 1.0, zoom );
                replot();
            }
        } );

        /* Construct and populate the plot panel with the 3D plot itself
         * and a transparent layer for doodling blobs on. */
        plotPanel_ = new JPanel();
        plotPanel_.setOpaque( false );
        blobPanel_ = new BlobPanel() {
            protected void blobCompleted( Shape blob ) {
                Insets insets = plot_.getInsets();
                AffineTransform trans =
                    AffineTransform.getTranslateInstance( -insets.left,
                                                          -insets.top );
                Shape transBlob =
                    new Area( blob ).createTransformedArea( trans );
                addNewSubsets( plot_.getPlottedPointIterator()
                                    .getContainedPoints( transBlob ) );
            }
        };
        blobAction_ = blobPanel_.getBlobAction();
        plotPanel_.setLayout( new OverlayLayout( plotPanel_ ) );
        plotPanel_.add( blobPanel_ );
        plotPanel_.add( plot_ );

        /* Listen for topcat actions. */
        getPointSelectors().addTopcatListener( this );

        /* Arrange that mouse dragging on the plot component will rotate
         * the view. */
        DragListener rotListener = new DragListener();
        plot_.addMouseMotionListener( rotListener );
        plot_.addMouseListener( rotListener );

        /* Arrange that clicking on a point will activate it. */
        plot_.addMouseListener( new PointClickListener() );

        /* Add a status line. */
        plotStatus_ = new CountsLabel( new String[] {
            "Potential", "Included", "Visible",
        } );
        plotStatus_.setMaximumSize( new Dimension( Integer.MAX_VALUE,
                                                   plotStatus_
                                                  .getMaximumSize().height ) );
        getStatusBox().add( plotStatus_ );

        /* Action for reorienting the plot. */
        Action reorientAction = new BasicAction( "Reorient", ResourceIcon.XYZ,
                                                 "Reorient the plot to initial"
                                               + " position" ) {
            public void actionPerformed( ActionEvent evt ) {
                setRotation( INITIAL_ROTATION );
                zoom_ = 1.0;
                replot();
            }
        };

        /* Action for selecting subset from visible points. */
        fromVisibleAction_ = new BasicAction( "New subset from visible",
                                              ResourceIcon.VISIBLE_SUBSET,
                                              "Define a new row subset " +
                                              "containing only currently " +
                                              "visible points" ) {
            public void actionPerformed( ActionEvent evt ) {
                addNewSubsets( plot_.getPlottedPointIterator().getAllPoints() );
            }
        };

        /* Model to toggle fogged rendering. */
        fogModel_ = new ToggleButtonModel( "Fog", ResourceIcon.FOG,
                                           "Select whether fog obscures " +
                                           "distant points" );
        fogModel_.setSelected( true );
        fogModel_.addActionListener( getReplotListener() );

        /* Model to toggle antialiasing. */
        antialiasModel_ = new ToggleButtonModel( "Antialias",
                                                 ResourceIcon.ANTIALIAS,
                                                 "Select whether text is " +
                                                 "antialiased" );
        antialiasModel_.setSelected( false );
        antialiasModel_.addActionListener( getReplotListener() );

        /* Model to keep the Y axis facing upwards. */
        northModel_ = new ToggleButtonModel( "Stay Upright",
                                             ResourceIcon.NORTH,
                                             "Select whether the Z axis is "
                                           + "always vertical on the screen" );
        northModel_.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent evt ) {
                setRotation( rotation_ );
                getReplotListener().actionPerformed( evt );
            }
        } );
        northModel_.setSelected( false );

        /* Construct a new menu for general plot operations. */
        JMenu plotMenu = new JMenu( "Plot" );
        plotMenu.setMnemonic( KeyEvent.VK_P );
        plotMenu.add( getRescaleAction() );
        plotMenu.add( reorientAction );
        plotMenu.add( northModel_.createMenuItem() );
        plotMenu.add( getAxisEditAction() );
        plotMenu.add( getGridModel().createMenuItem() );
        plotMenu.add( getReplotAction() );
        getJMenuBar().add( plotMenu );

        /* Construct a new menu for rendering options. */
        JMenu renderMenu = new JMenu( "Rendering" );
        renderMenu.setMnemonic( KeyEvent.VK_R );
        renderMenu.add( fogModel_.createMenuItem() );
        renderMenu.add( antialiasModel_.createMenuItem() );
        getJMenuBar().add( renderMenu );

        /* Construct a new menu for subset options. */
        JMenu subsetMenu = new JMenu( "Subsets" );
        subsetMenu.setMnemonic( KeyEvent.VK_S );
        subsetMenu.add( blobAction_ );
        if ( plot_.canZoom() ) {
            subsetMenu.add( fromVisibleAction_ );
        }
        getJMenuBar().add( subsetMenu );

        /* Add actions to the toolbar. */
        getToolBar().add( getRescaleAction() );
        getToolBar().add( reorientAction );
        getToolBar().add( northModel_.createToolbarButton() );
        getToolBar().add( getGridModel().createToolbarButton() );
        getToolBar().add( fogModel_.createToolbarButton() );
        getToolBar().add( blobAction_ );
        if ( plot_.canZoom() ) {
            getToolBar().add( fromVisibleAction_ );
        }

        /* Set initial rotation. */
        setRotation( INITIAL_ROTATION );
        replot();
    }

    /**
     * Sets the viewing angle.
     *
     * @param   matrix  9-element array giving rotation of data space
     */
    public void setRotation( double[] matrix ) {
        double[] rot = (double[]) matrix.clone();
        if ( northModel_.isSelected() ) {
            double theta = Math.atan2( rot[ 2 ], rot[ 5 ] );
            double[] correction = 
                rotate( rot, new double[] { 0., 0., 1., }, theta );
            rot = Matrices.mmMult( rot, correction );
        }
        rotation_ = rot;
    }

    protected JComponent getPlot() {
        return plotPanel_;
    }

    protected PlotState createPlotState() {
        return new Plot3DState();
    }

    public PlotState getPlotState() {
        Plot3DState state = (Plot3DState) super.getPlotState();

        /* Configure the state with this window's current viewing angles
         * and zoom state. */
        state.setRotation( rotation_ );
        state.setRotating( isRotating_ );
        state.setZoomScale( zoom_ );

        /* Configure rendering options. */
        state.setFogginess( fogModel_.isSelected() ? 2.0 : 0.0 );
        state.setAntialias( antialiasModel_.isSelected() );

        /* Return. */
        return state;
    }

    public Rectangle getPlotBounds() {
        return plot_.getPlotBounds();
    }

    public StyleSet getDefaultStyles( int npoint ) {
        if ( npoint > 20000 ) {
            return PlotWindow.STYLE_SETS[ 0 ];
        }
        else if ( npoint > 2000 ) {
            return PlotWindow.STYLE_SETS[ 1 ];
        }
        else if ( npoint > 200 ) {
            return PlotWindow.STYLE_SETS[ 2 ];
        }
        else if ( npoint > 20 ) {
            return PlotWindow.STYLE_SETS[ 3 ];
        }
        else if ( npoint >= 1 ) {
            return PlotWindow.STYLE_SETS[ 4 ];
        }
        else {
            return PlotWindow.STYLE_SETS[ 1 ];
        }
    }

    protected void doReplot( PlotState state, Points points ) {
        blobPanel_.setActive( false );
        plot_.setPoints( points );
        plot_.setState( (Plot3DState) state );
        plot_.repaint();
    }

    /**
     * Returns the model which toggles whether the orientation of the plot
     * always points up on the screen.
     *
     * @return  keep north action
     */
    public ToggleButtonModel getNorthModel() {
        return northModel_;
    }

    /*
     * TopcatListener implementation.
     */
    public void modelChanged( TopcatEvent evt ) {
        if ( evt.getCode() == TopcatEvent.ROW ) {
            Object datum = evt.getDatum();
            if ( datum instanceof Long ) {
                TopcatModel tcModel = evt.getModel();
                PointSelection psel = plot_.getState().getPointSelection();
                long lrow = ((Long) datum).longValue();
                long[] lps = psel.getPointsForRow( tcModel, lrow );
                int[] ips = new int[ lps.length ];
                for ( int i = 0; i < lps.length; i++ ) {
                    ips[ i ] = Tables.checkedLongToInt( lps[ i ] );
                }
                plot_.setActivePoints( ips );
            }
            else {
                assert false;
            }
        }
    }

    /**
     * Takes a view rotation matrix and adds to it the effect of rotations
     * about X and Y directions.
     *
     * @param   base  9-element array giving initial view rotation matrix
     * @param   phi   angle to rotate around Y axis
     * @param   psi   angle to rotate around X axis
     * @return  9-element array giving combined rotation matrix
     */
    private static double[] rotateXY( double[] base, double phi, double psi ) {
        double[] rotX = rotate( base, new double[] { 0., 1., 0. }, phi );
        double[] rotY = rotate( base, new double[] { 1., 0., 0. }, psi );
        return Matrices.mmMult( Matrices.mmMult( base, rotX ), rotY );
    }

    /**
     * Calculates a rotation matrix for rotating around a screen axis
     * by a given angle.  Note this axis is in the view space, not the
     * data space.
     * 
     * @param   base  rotation matrix defining the view orientation
     *                (9-element array)
     * @param   screenAxis  axis in view space about which rotation is required
     *                      (3-element array)
     * @param   theta   rotation angle in radians
     */
    private static double[] rotate( double[] base, double[] screenAxis,
                                    double theta ) {

        /* Calculate the unit vector in data space corresponding to the 
         * given screen axis. */
        double[] axis = Matrices.mvMult( Matrices.invert( base ), screenAxis );
        double[] a = Matrices.normalise( axis );
        double x = a[ 0 ];
        double y = a[ 1 ];
        double z = a[ 2 ];

        /* Calculate and return the rotation matrix (Euler angles).
         * This algebra copied from SLALIB DAV2M (Pal version). */
        double s = Math.sin( theta );
        double c = Math.cos( theta );
        double w = 1.0 - c;
        return new double[] {
            x * x * w + c,
            x * y * w + z * s,
            x * z * w - y * s,
            x * y * w - z * s,
            y * y * w + c,
            y * z * w + x * s,
            x * z * w + y * s,
            y * z * w - x * s,
            z * z * w + c,
        };
    }

    /**
     * Listener which interprets drag gestures on the plotting surface 
     * as requests to rotate the viewing angles.
     */
    private class DragListener extends MouseAdapter
                               implements MouseMotionListener {

        private Point posBase_;
        private double[] rotBase_;
        private boolean relevant_;

        public void mousePressed( MouseEvent evt ) {
            relevant_ = plot_.getPlotBounds().contains( evt.getPoint() );
        }

        public void mouseDragged( MouseEvent evt ) {
            if ( ! relevant_ ) {
                return;
            }
            isRotating_ = true;
            Point pos = evt.getPoint(); 
            if ( posBase_ == null ) {
                posBase_ = pos;
                rotBase_ = Plot3DWindow.this.rotation_;
            }
            else {

                /* Work out the amounts by which the user wants to rotate
                 * in the 'horizontal' and 'vertical' directions respectively
                 * (these directions are relative to the current orientation
                 * of the view). */
                double scale = Math.min( plot_.getWidth(), plot_.getHeight() );
                double xf = - ( pos.x - posBase_.x ) / scale / zoom_;
                double yf = - ( pos.y - posBase_.y ) / scale / zoom_;

                /* Turn these into angles.  Phi and Psi are the rotation
                 * angles around the screen vertical and horizontal axes
                 * respectively. */
                double phi = xf * Math.PI / 2.;
                double psi = yf * Math.PI / 2.;
                setRotation( rotateXY( rotBase_, phi, psi ) );
                replot();
            }
        }

        public void mouseMoved( MouseEvent evt ) {
            posBase_ = null;
            rotBase_ = null;
        }

        public void mouseReleased( MouseEvent evt ) {
            if ( isRotating_ ) {
                isRotating_ = false;
                replot();
            }
        }
    }

    /**
     * Watches for points clicked and activates the corresponding row(s) 
     * if they are.
     */
    private class PointClickListener extends MouseAdapter {
        public void mouseClicked( MouseEvent evt ) {
            int butt = evt.getButton();
            if ( butt == MouseEvent.BUTTON1 ) {

                /* Get the position in plot coordinates. */
                JComponent comp = (JComponent) evt.getComponent();
                assert comp == plot_;
                Insets insets = comp.getInsets();
                Point point = evt.getPoint();
                point.translate( - insets.left, - insets.top );

                /* Get the closest plotted point to this. */
                int ip = plot_.getPlottedPointIterator()
                              .getClosestPoint( point, 4 );

                /* Highlight if there is one. */
                if ( ip >= 0 ) {
                    PointSelection psel = plot_.getState().getPointSelection();
                    psel.getPointTable( ip )
                        .highlightRow( psel.getPointRow( ip ) );
                }
                else {
                    plot_.setActivePoints( new int[ 0 ] );
                }
            }
        }
    }
}
