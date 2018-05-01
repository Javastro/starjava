package uk.ac.starlink.topcat.activate;

import java.awt.event.ActionListener;
import java.io.IOException;
import javax.swing.JComponent;

/**
 * Defines a GUI component that the user can interact with to specify
 * an activation action.  This is a GUI factory for Activator instances.
 *
 * @author   Mark Taylor
 * @since    23 Jan 2018
 */
public interface ActivatorConfigurator {

    /**
     * Returns the GUI component with which the user can interact.
     *
     * @return  configuration panel
     */
    JComponent getPanel();

    /**
     * Constructs an activator in accordance with the current state of
     * this GUI.  If the current state not an adequate specification,
     * null may be returned.  In that case, the getConfigMessage method
     * should generally provide an explanation.
     *
     * <p>This method may be called often, so should be cheap to invoke.
     * The idea is that it will be called immediately before the activator
     * is used, so the returned object may fix, rather than arrange to
     * gather at a later date, information acquired from the GUI.
     *
     * @return   currently configured activator, or null
     * @see   #getConfigMessage
     */
    Activator getActivator();

    /**
     * Returns a short status message for display to the user.
     * This is supposed to be a comment, if one is needed, on the
     * current state of the configuration.
     *
     * <p>As a rule, exactly one of this method and the
     * <code>getActivator</code> should return null.
     * If there is no activator, this method should return some
     * reason why the configuration is incorrect or incomplete,
     * and otherwise it should return null.
     *
     * @return  message, typically indicating configuration problems, or null
     */
    String getConfigMessage();

    /**
     * Adds a listener that will be informed when the GUI state changes
     * in such a way that the result of <code>getActivator</code> or
     * <code>getConfigMessage</code> may change.
     *
     * @param  listener  listener to add
     */
    void addActionListener( ActionListener listener );

    /**
     * Removes a previously added listener.
     *
     * @param  listener  listener to remove
     */
    void removeActionListener( ActionListener listener );
}