package uk.ac.starlink.votable.dom;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
//DOM3 import org.w3c.dom.TypeInfo;

public class DelegatingElement extends DelegatingNode implements Element {

    private final Element base_;
    private final DelegatingDocument doc_;

    protected DelegatingElement( Element base, DelegatingDocument doc ) {
        super( base, doc );
        base_ = base;
        doc_ = doc;
    }

    //
    // Level 2 implementation.
    //

    public String getTagName() {
        return base_.getTagName();
    }

    public String getAttribute( String name ) {
        return base_.getAttribute( name );
    }

    public void setAttribute( String name, String value ) {
        base_.setAttribute( name, value );
    }

    public void removeAttribute( String name ) {
        base_.removeAttribute( name );
    }

    public Attr getAttributeNode( String name ) {
        return (Attr) doc_.getDelegator( base_.getAttributeNode( name ) );
    }

    public Attr setAttributeNode( Attr newAttr ) {
        Attr newAttrBase = DelegatingAttr.getBaseAttr( newAttr, doc_ );
        return (Attr) doc_.getDelegator( base_
                                        .setAttributeNode( newAttrBase ) );
    }

    public Attr removeAttributeNode( Attr oldAttr ) {
        Attr oldAttrBase = DelegatingAttr.getBaseAttr( oldAttr, null );
        return (Attr) doc_.getDelegator( base_
                                        .removeAttributeNode( oldAttrBase ) );
    }

    public NodeList getElementsByTagName( String name ) {
        return doc_.createDelegatingNodeList( base_
                                             .getElementsByTagName( name ) );
    }

    public String getAttributeNS( String namespaceURI, String localName ) {
        return base_.getAttributeNS( namespaceURI, localName );
    }

    public void setAttributeNS( String namespaceURI, String qualifiedName,
                                String value ) {
        base_.setAttributeNS( namespaceURI, qualifiedName, value );
    }

    public void removeAttributeNS( String namespaceURI, String localName ) {
        base_.removeAttributeNS( namespaceURI, localName );
    }

    public Attr getAttributeNodeNS( String namespaceURI, String localName ) {
        return (Attr) doc_.getDelegator( base_
                                        .getAttributeNodeNS( namespaceURI,
                                                             localName ) );
    }

    public Attr setAttributeNodeNS( Attr attr ) {
        Attr attrBase = DelegatingAttr.getBaseAttr( attr, doc_ );
        return (Attr) doc_.getDelegator( base_.setAttributeNodeNS( attrBase ) );
    }

    public NodeList getElementsByTagNameNS( String namespaceURI,
                                            String localName ) {
        return doc_
              .createDelegatingNodeList( 
                   base_.getElementsByTagNameNS( namespaceURI, localName ) );
    }

    public boolean hasAttribute( String name ) {
        return base_.hasAttribute( name );
    }

    public boolean hasAttributeNS( String namespaceURI, String localName ) {
        return base_.hasAttributeNS( namespaceURI, localName );
    }

//DOM3     //
//DOM3     // Level 3 implementation.
//DOM3     //
//DOM3 
//DOM3     public TypeInfo getSchemaTypeInfo() {
//DOM3         return base_.getSchemaTypeInfo();
//DOM3     }
//DOM3 
//DOM3     public void setIdAttribute( String name, boolean isId ) {
//DOM3         base_.setIdAttribute( name, isId );
//DOM3     }
//DOM3 
//DOM3     public void setIdAttributeNS( String namespaceURI,
//DOM3                                   String localName, boolean isId ) {
//DOM3         base_.setIdAttributeNS( namespaceURI, localName, isId );
//DOM3     }
//DOM3 
//DOM3     public void setIdAttributeNode( Attr idAttr, boolean isId ) {
//DOM3         base_.setIdAttributeNode( idAttr, isId );
//DOM3     }
}