/*
 * DPP - Serious Distributed Pair Programming
 * (c) Freie Universitaet Berlin - Fachbereich Mathematik und Informatik - 2006
 * (c) Riad Djemili - 2006
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 1, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package de.fu_berlin.inf.dpp.ui;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.part.ViewPart;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket;

import de.fu_berlin.inf.dpp.Saros;
import de.fu_berlin.inf.dpp.Saros.ConnectionState;
import de.fu_berlin.inf.dpp.net.IConnectionListener;
import de.fu_berlin.inf.dpp.net.internal.SubscriptionListener;
import de.fu_berlin.inf.dpp.ui.actions.ConnectDisconnectAction;
import de.fu_berlin.inf.dpp.ui.actions.DeleteContactAction;
import de.fu_berlin.inf.dpp.ui.actions.InviteAction;
import de.fu_berlin.inf.dpp.ui.actions.NewContactAction;
import de.fu_berlin.inf.dpp.ui.actions.RenameContactAction;
import de.fu_berlin.inf.dpp.ui.actions.SkypeAction;

/**
 * This view displays the roster (also known as contact list) of the local user.
 * 
 * @author rdjemili
 */
public class RosterView extends ViewPart implements IConnectionListener,
        IRosterTree {

    private static Logger logger = Logger.getLogger(RosterView.class);

    private TreeViewer viewer;

    private Roster roster;

    private XMPPConnection connection;

    // actions
    // private Action messagingAction;

    private Action inviteAction;

    private RenameContactAction renameContactAction;

    private DeleteContactAction deleteContactAction;

    private SkypeAction skypeAction;

    /**
     * An item of the roster tree. Can be either a group or a single contact.
     * 
     * @author rdjemili
     */
    private interface TreeItem {

        /**
         * @return all child items of this tree item.
         */
        Object[] getChildren();
    }

    /**
     * A group item which holds a number of users.
     */
    private class GroupItem implements TreeItem {
        private final RosterGroup group;

        public GroupItem(RosterGroup group) {
            this.group = group;
        }

        /*
         * (non-Javadoc)
         * 
         * @see de.fu_berlin.inf.dpp.ui.RosterView.TreeItem
         */
        public Object[] getChildren() {
            return this.group.getEntries().toArray();
        }

        @Override
        public String toString() {
            return this.group.getName();
        }
    }

    /**
     * A group item that holds all users that don't belong to another group.
     */
    private class UnfiledGroupItem implements TreeItem {

        /*
         * (non-Javadoc)
         * 
         * @see de.fu_berlin.inf.dpp.ui.RosterView.TreeItem
         */
        public Object[] getChildren() {
            return RosterView.this.roster.getUnfiledEntries().toArray();
        }

        @Override
        public String toString() {
            return "Buddies";
        }
    }

    /**
     * Provide tree content.
     */
    private class TreeContentProvider implements IStructuredContentProvider,
            ITreeContentProvider {

        /*
         * @see org.eclipse.jface.viewers.IContentProvider
         */
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
        }

        /*
         * @see org.eclipse.jface.viewers.IContentProvider
         */
        public void dispose() {
        }

        /*
         * @see org.eclipse.jface.viewers.IStructuredContentProvider
         */
        public Object[] getElements(Object parent) {
            if (parent.equals(getViewSite())
                    && (RosterView.this.roster != null)) {
                List<TreeItem> groups = new LinkedList<TreeItem>();
                for (RosterGroup rg : RosterView.this.roster.getGroups()) {
                    GroupItem item = new GroupItem(rg);
                    groups.add(item);
                }

                // for (Iterator it = roster.getGroups(); it.hasNext();) {
                // GroupItem item = new GroupItem((RosterGroup) it.next());
                // groups.add(item);
                // }

                groups.add(new UnfiledGroupItem());

                return groups.toArray();
            }

            return new Object[0];
        }

        /*
         * @see org.eclipse.jface.viewers.ITreeContentProvider
         */
        public Object getParent(Object child) {
            return null; // TODO
        }

        /*
         * @see org.eclipse.jface.viewers.ITreeContentProvider
         */
        public Object[] getChildren(Object parent) {
            if (parent instanceof TreeItem) {
                return ((TreeItem) parent).getChildren();
            }

            return new Object[0];
        }

        /*
         * @see org.eclipse.jface.viewers.ITreeContentProvider
         */
        public boolean hasChildren(Object parent) {
            if (parent instanceof TreeItem) {
                Object[] children = ((TreeItem) parent).getChildren();
                return children.length > 0;
            }

            return false;
        }
    }

    /**
     * Shows user name and state in parenthesis.
     */
    private class ViewLabelProvider extends LabelProvider {
        private final Image groupImage = SarosUI.getImage("icons/group.png");

        private final Image personImage = SarosUI.getImage("icons/user.png");

        @Override
        public String getText(Object obj) {
            if (obj instanceof RosterEntry) {
                RosterEntry entry = (RosterEntry) obj;

                String label = entry.getName();

                // show JID if entry has no nickname
                if (label == null) {
                    label = entry.getUser();
                }

                // append presence information if available
                Presence presence = RosterView.this.roster.getPresence(entry
                        .getUser());
                RosterEntry e = RosterView.this.roster
                        .getEntry(entry.getUser());
                if (e.getStatus() == RosterPacket.ItemStatus.SUBSCRIPTION_PENDING) {
                    label = label + " (wait for permission)";
                } else if (presence != null) {
                    label = label + " (" + presence.getType() + ")";
                }

                return label;
            }

            return obj.toString();
        }

        @Override
        public Image getImage(Object element) {
            return element instanceof RosterEntry ? this.personImage
                    : this.groupImage;
        }
    }

    /**
     * A sorter that orders by presence and then by name.
     */
    private class NameSorter extends ViewerSorter {
        @Override
        public int compare(Viewer viewer, Object elem1, Object elem2) {

            // sort by presence
            if (elem1 instanceof RosterEntry) {
                RosterEntry entry1 = (RosterEntry) elem1;

                if (elem2 instanceof RosterEntry) {
                    RosterEntry entry2 = (RosterEntry) elem2;

                    String user1 = entry1.getUser();
                    boolean presence1 = RosterView.this.roster
                            .getPresence(user1) != null;

                    String user2 = entry2.getUser();
                    boolean presence2 = RosterView.this.roster
                            .getPresence(user2) != null;

                    if (presence1 && !presence2) {
                        return -1;
                    } else if (!presence1 && presence2) {
                        return 1;
                    }
                }
            }

            // otherwise use default order
            return super.compare(viewer, elem1, elem2);
        }
    }

    /**
     * Creates an roster view..
     */
    public RosterView() {
    }

    /**
     * This is a callback that will allow us to create the viewer and initialize
     * it.
     */
    @Override
    public void createPartControl(Composite parent) {
        this.viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL
                | SWT.V_SCROLL);
        this.viewer.setContentProvider(new TreeContentProvider());
        this.viewer.setLabelProvider(new ViewLabelProvider());
        this.viewer.setSorter(new NameSorter());
        this.viewer.setInput(getViewSite());
        this.viewer.expandAll();

        makeActions();
        hookContextMenu();
        // hookDoubleClickAction();
        contributeToActionBars();
        updateEnablement();

        Saros saros = Saros.getDefault();
        saros.addListener(this);

        connectionStateChanged(saros.getConnection(), saros
                .getConnectionState());
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    @Override
    public void setFocus() {
        this.viewer.getControl().setFocus();
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.fu_berlin.inf.dpp.listeners.IConnectionListener
     */
    public void connectionStateChanged(XMPPConnection connection,
            final ConnectionState newState) {
        if (newState == ConnectionState.CONNECTED) {
            // roster = Saros.getDefault().getRoster();
            this.roster = connection.getRoster();
            this.connection = connection;
            attachRosterListener();

        } else if (newState == ConnectionState.NOT_CONNECTED) {
            this.roster = null;
        }

        refreshRosterTree(true);

        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                updateStatusLine(newState);
                updateEnablement();
            }
        });
    }

    /**
     * Needs to called from an UI thread.
     */
    private void updateEnablement() {
        this.viewer.getControl().setEnabled(Saros.getDefault().isConnected());
    }

    /**
     * Needs to called from an UI thread.
     */
    private void updateStatusLine(final ConnectionState newState) {
        IStatusLineManager statusLine = getViewSite().getActionBars()
                .getStatusLineManager();
        statusLine.setMessage(SarosUI.getDescription(newState));
    }

    private void attachRosterListener() {

        this.connection.addPacketListener(new SubscriptionListener(
                this.connection, this), new PacketTypeFilter(Presence.class));

        this.connection.getRoster().addRosterListener(new RosterListener() {
            public void entriesAdded(Collection<String> addresses) {
            }

            public void entriesUpdated(Collection<String> addresses) {
                for (String address : addresses) {
                    logger.debug(address
                            + ": "
                            + connection.getRoster().getEntry(address)
                                    .getType()
                            + ", "
                            + connection.getRoster().getEntry(address)
                                    .getStatus());
                }

                refreshRosterTree(true);
            }

            public void entriesDeleted(Collection<String> addresses) {
                refreshRosterTree(false);
            }

            public void presenceChanged(Presence presence) {
                logger.debug(presence.getFrom() + ": " + presence);
                refreshRosterTree(true);
            }
        });
    }

    /**
     * Refreshes the roster tree.
     * 
     * @param updateLabels
     *            <code>true</code> if item labels (might) have changed.
     *            <code>false</code> otherwise.
     */
    public void refreshRosterTree(final boolean updateLabels) {
        if (this.viewer.getControl().isDisposed()) {
            return;
        }

        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                RosterView.this.viewer.refresh(updateLabels);
                RosterView.this.viewer.expandAll();
            }
        });
    }

    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#PopupMenu");
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {
            public void menuAboutToShow(IMenuManager manager) {
                RosterView.this.fillContextMenu(manager);
            }
        });

        Menu menu = menuMgr.createContextMenu(this.viewer.getControl());

        this.viewer.getControl().setMenu(menu);
        getSite().registerContextMenu(menuMgr, this.viewer);
    }

    // private void hookDoubleClickAction() {
    // this.viewer.addDoubleClickListener(new IDoubleClickListener() {
    // public void doubleClick(DoubleClickEvent event) {
    // if (RosterView.this.messagingAction.isEnabled()) {
    // RosterView.this.messagingAction.run();
    // }
    // }
    // });
    // }

    private void contributeToActionBars() {
        IActionBars bars = getViewSite().getActionBars();

        IMenuManager menuManager = bars.getMenuManager();
        // menuManager.add(this.messagingAction);
        menuManager.add(this.inviteAction);
        // menuManager.add(new TestJoinWizardAction());
        menuManager.add(new Separator());

        IToolBarManager toolBarManager = bars.getToolBarManager();
        toolBarManager.add(new ConnectDisconnectAction());
        toolBarManager.add(new NewContactAction());
    }

    private void fillContextMenu(IMenuManager manager) {
        // manager.add(this.messagingAction);
        manager.add(this.skypeAction);
        manager.add(this.inviteAction);
        manager.add(new Separator());
        manager.add(this.renameContactAction);
        manager.add(this.deleteContactAction);

        // Other plug-ins can contribute there actions here
        manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
    }

    private void makeActions() {
        // this.messagingAction = new MessagingAction(this.viewer);
        this.skypeAction = new SkypeAction(this.viewer);
        this.inviteAction = new InviteAction(this.viewer);
        this.renameContactAction = new RenameContactAction(this.viewer);
        this.deleteContactAction = new DeleteContactAction(this.viewer);
    }
}