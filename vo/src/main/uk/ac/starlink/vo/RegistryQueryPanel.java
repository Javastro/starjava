package uk.ac.starlink.vo;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.net.MalformedURLException;
import java.net.URL;
import javax.swing.Box;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.MutableComboBoxModel;

/**
 * Component which allows the user to select a registry to interrogate
 * and a query string representing the query to be done.
 *
 * @author   Mark Taylor (Starlink)
 * @since    23 Dec 2004
 */
public class RegistryQueryPanel extends JPanel {

    private JComboBox urlSelector_;
    private JComboBox querySelector_;

    /** Known registry URLs.  */
    public static String[] KNOWN_REGISTRIES = new String[] {
        RegistryInterrogator.DEFAULT_URL.toString(),
    };

    /**
     * Constructor.
     */
    public RegistryQueryPanel() {
        super( new BorderLayout() );
        JComponent qBox = Box.createVerticalBox();
        add( qBox, BorderLayout.CENTER );

        /* Registry URL selector. */
        JComponent urlLine = Box.createHorizontalBox();
        urlSelector_ = new JComboBox( KNOWN_REGISTRIES ) {
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        urlSelector_.setSelectedIndex( 0 );
        urlLine.add( new JLabel( "Registry: " ) );
        urlLine.add( urlSelector_ );
        urlLine.add( Box.createHorizontalGlue() );
        qBox.add( urlLine );
        qBox.add( Box.createVerticalStrut( 5 ) );

        /* Query text selector. */
        JComponent queryLine = Box.createHorizontalBox();
        querySelector_ = new JComboBox();
        querySelector_.setEditable( true );
        queryLine.add( new JLabel( "Query: " ) );
        queryLine.add( querySelector_ );
        qBox.add( queryLine );
    }

    /**
     * Installs a set of custom queries which the user can choose from.
     * If the combo box is editable (it is by default) so the user can
     * still enter free-form queries.
     *
     * @param  queries  list of query strings
     */
    public void setPresetQueries( String[] queries ) {
        querySelector_.setModel( new DefaultComboBoxModel( queries ) );
        querySelector_.setSelectedIndex( 0 );
    }

    /**
     * Returns the selector used for registry URL.
     *
     * @return  URL selector
     */
    public JComboBox getUrlSelector() {
        return urlSelector_;
    }

    /**
     * Returns the selector used for the query text.
     *
     * @return  query text selector
     */
    public JComboBox getQuerySelector() {
        return querySelector_;
    }

    /**
     * Returns a RegistryQuery object which can perform the query currently
     * specified by the state of this component.  Some checking on whether
     * the fields are filled in sensibly is done; if they are not, an
     * informative MalformedURLException will be thrown.
     *
     * @return  query object
     */
    public RegistryQuery getRegistryQuery() throws MalformedURLException {
        String regServ = (String) urlSelector_.getSelectedItem();
        String query = (String) querySelector_.getSelectedItem();
        if ( query == null || query.trim().length() == 0 ) {
            throw new MalformedURLException( "Query URL is blank" );
        }
        URL regURL;
        if ( regServ == null || regServ.trim().length() == 0 ) {
            throw new MalformedURLException( "Registry URL is blank" );
        }
        try {
            regURL = new URL( regServ );
        }
        catch ( MalformedURLException e ) {
            throw new MalformedURLException( "Bad registry URL: " + regServ );
        }

        /* If this query looks OK, add it to the combo box model. */
        ComboBoxModel qModel = querySelector_.getModel();
        if ( qModel instanceof MutableComboBoxModel ) {
            boolean present = false;
            for ( int i = 0; ! present && i < qModel.getSize(); i++ ) {
                if ( query.equals( qModel.getElementAt( i ) ) ) {
                    present = true;
                }
            }
            if ( ! present ) {
                ((MutableComboBoxModel) qModel).addElement( query );
            }
        }
        return new RegistryQuery( regURL, query );
    }

    public void setEnabled( boolean enabled ) {
        super.setEnabled( enabled );
        urlSelector_.setEnabled( enabled );
        querySelector_.setEnabled( enabled );
    }

}