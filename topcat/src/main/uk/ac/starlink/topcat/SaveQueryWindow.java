package uk.ac.starlink.topcat;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.StarTableOutput;
import uk.ac.starlink.table.gui.FilestoreTableSaveDialog;
import uk.ac.starlink.table.gui.SQLWriteDialog;
import uk.ac.starlink.table.gui.SystemTableSaveDialog;
import uk.ac.starlink.table.gui.TableLoadChooser;
import uk.ac.starlink.table.gui.TableSaveChooser;
import uk.ac.starlink.table.gui.TableSaveDialog;
import uk.ac.starlink.util.gui.ShrinkWrapper;

public class SaveQueryWindow extends QueryWindow {

    private final TableSaveChooser chooser_;

    public SaveQueryWindow( final TopcatModel tcModel, StarTableOutput sto,
                            TableLoadChooser loadChooser, Component parent ) {
        super( "Save Table", parent, false, true );

        /* Place a progress bar. */
        final JProgressBar progBar = placeProgressBar();

        /* Construct and configure the main table chooser widget. */
        chooser_ = new TableSaveChooser( sto,
                                         new TableSaveDialog[] {
                                             new FilestoreTableSaveDialog(),
                                             new SystemTableSaveDialog(),
                                         } ) {
            public StarTable getTable() {
                return tcModel.getApparentStarTable();
            }
            public void done() {
                super.done();
                SaveQueryWindow.this.dispose();
            }
        };
        chooser_.setProgressBar( progBar );
        chooser_.configureFromLoader( loadChooser );

        /* Prepare and place a component which will hold the chooser
         * widget and some other bits and pieces. */
        JComponent mainBox = Box.createVerticalBox();
        getAuxControlPanel().add( mainBox );

        /* Row subset. */
        JComponent subsetLine = Box.createHorizontalBox();
        subsetLine.add( Box.createHorizontalStrut( 5 ) );
        subsetLine.add( new JLabel( "Row Subset: " ) );
        subsetLine.add( new ShrinkWrapper( 
                            new JComboBox( 
                                tcModel.getSubsetSelectionModel() ) ) );
        subsetLine.add( Box.createHorizontalGlue() );
        mainBox.add( subsetLine );
        mainBox.add( Box.createVerticalStrut( 5 ) );

        /* Sort order. */
        JComponent sortLine = Box.createHorizontalBox();
        sortLine.add( Box.createHorizontalStrut( 5 ) );
        sortLine.add( new JLabel( "Sort Order: " ) );
        sortLine.add( new ShrinkWrapper( 
                          new JComboBox( tcModel.getSortSelectionModel() ) ) );
        sortLine.add( new UpDownButton( tcModel.getSortSenseModel() ) );
        sortLine.add( Box.createHorizontalGlue() );
        mainBox.add( sortLine );

        /* Chooser widget. */
        JComponent chooserLine = Box.createHorizontalBox();
        chooserLine.add( chooser_ );
        chooserLine.add( Box.createHorizontalGlue() );
        mainBox.add( chooserLine );

        /* Toolbar buttons. */
        List saveActList = new ArrayList();
        saveActList.addAll( Arrays.asList( chooser_.getSaveDialogActions() ) );
        saveActList.add( chooser_
                        .createSaveDialogAction( new SQLWriteDialog() ) );
        for ( Iterator it = saveActList.iterator(); it.hasNext(); ) {
            getToolBar().add( (Action) it.next() );
        }
        getToolBar().addSeparator();

        /* Help button. */
        addHelp( "SaveQueryWindow" );
    }

    public boolean perform() {
        return false;
    }
}
