package uk.ac.starlink.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * A DataSource implementation based on a {@link java.io.File}.
 *
 * @author   Mark Taylor (Starlink)
 */
public class FileDataSource extends DataSource {

    private File file;

    /**
     * Creates a new FileDataSource from a File object and a position string.
     *
     * @param  file  the file
     * @param  position  the source's position attribute 
     *         (indicates the relevant part of the file)
     * @throws  IOException  if <tt>file</tt> does not exist, cannot be read,
     *          or is a directory
     */
    public FileDataSource( File file, String position ) throws IOException {
        if ( ! file.exists() ) {
            throw new FileNotFoundException( "No such file " + file );
        }
        else if ( ! file.canRead() ) {
            throw new IOException( "No read permission on file " + file );
        }
        else if ( file.isDirectory() ) {
            throw new IOException( file + " is a directory" );
        }
        this.file = file;
        setName( file.toString() 
               + ( ( position != null ) ? ( '#' + position ) : "" ) );
        setPosition( position );
    }

    /**
     * Creates a new FileDataSource from a File object.
     *
     * @param  file  the file
     * @throws  IOException  if <tt>file</tt> does not exist, cannot be read,
     *          or is a directory
     */
    public FileDataSource( File file ) throws IOException {
        this( file, null );
    }

    protected InputStream getRawInputStream() throws IOException {
        return new FileInputStream( file );
    }

    /**
     * Returns the length of this file.
     *
     * return  file length
     */
    public long getRawLength() {
        return file.length();
    }

    /**
     * Returns the File object on which this <tt>FileDataSource</tt> is based.
     *
     * @return  the file
     */
    public File getFile() {
        return file;
    }

    public URL getURL() {
        URI baseURI = file.toURI();
        URI withfrag;
        try {
            withfrag = new URI( baseURI.getScheme(),
                                baseURI.getSchemeSpecificPart(),
                                getPosition() );
        }
        catch ( URISyntaxException e ) {
            throw new AssertionError( "What's wrong with URI " +
                                      baseURI.getScheme() + ':' +
                                      baseURI.getSchemeSpecificPart() + '#' +
                                      getPosition() + " ?" );
        }
        try {
            return withfrag.toURL();
        }
        catch ( MalformedURLException e ) {
            throw new AssertionError( "What's wrong with URL " + 
                                      withfrag + " ?" );
        }
    }
}
