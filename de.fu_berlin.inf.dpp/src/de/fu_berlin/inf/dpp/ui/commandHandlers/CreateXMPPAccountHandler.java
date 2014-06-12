package de.fu_berlin.inf.dpp.ui.commandHandlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import de.fu_berlin.inf.dpp.ui.util.WizardUtils;

public class CreateXMPPAccountHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        WizardUtils.openCreateXMPPAccountWizard(true);

        return null;
    }

}
