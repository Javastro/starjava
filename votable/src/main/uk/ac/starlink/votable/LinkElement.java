package uk.ac.starlink.votable;

import java.net.MalformedURLException;
import java.net.URL;
import org.w3c.dom.Element;

/**
 * Object representing a LINK element in a VOTable.
 *
 * @author   Mark Taylor (Starlink)
 */
public class LinkElement extends VOElement {

    /**
     * Constructs a LinkElement from a DOM element.
     *
     * @param   base  LINK element
     * @doc     owner document for new element
     */
    LinkElement( Element base, VODocument doc ) {
        super( base, doc, "LINK" );
    }

    /**
     * Returns the value of this LinkElement's 'href' attribute as a URL.
     * It is resolved against the base URL of this VOTable if it
     * represents a relative URL.
     *
     * @return  the URL represented by this LinkElement's 'href' attribute,
     *          or <tt>null</tt> if it has none
     * @throws  MalformedURLException in the case of a badly-formed URL
     */
    public URL getHref() throws MalformedURLException {
        return hasAttribute( "href" )
             ? getContextURL( getAttribute( "href" ) )
             : null;
    }
}
