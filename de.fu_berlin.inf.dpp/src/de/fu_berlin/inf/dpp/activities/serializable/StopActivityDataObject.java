package de.fu_berlin.inf.dpp.activities.serializable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamConverter;

import de.fu_berlin.inf.dpp.activities.IActivityDataObjectConsumer;
import de.fu_berlin.inf.dpp.activities.IActivityDataObjectReceiver;
import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.util.xstream.JIDConverter;

/**
 * A StopActivityDataObject is used for signaling to a user that he should be
 * stopped or started (meaning that no more activityDataObjects should be
 * generated by this user).
 */
@XStreamAlias("stopActivity")
public class StopActivityDataObject extends AbstractActivityDataObject {

    @XStreamAsAttribute
    @XStreamConverter(JIDConverter.class)
    protected JID initiator;

    // the user who has to be locked / unlocked
    @XStreamAsAttribute
    @XStreamConverter(JIDConverter.class)
    protected JID user;

    public enum Type {
        LOCKREQUEST, UNLOCKREQUEST
    }

    @XStreamAsAttribute
    protected Type type;

    public enum State {
        INITIATED, ACKNOWLEDGED
    }

    @XStreamAsAttribute
    protected State state;

    // a stop activityDataObject has a unique id
    @XStreamAsAttribute
    protected String stopActivityID;

    protected static Random random = new Random();

    public StopActivityDataObject(JID source, JID initiator, JID user,
        Type type, State state) {

        super(source);
        this.initiator = initiator;
        this.user = user;
        this.state = state;
        this.type = type;
        this.stopActivityID = new SimpleDateFormat("HHmmssSS")
            .format(new Date())
            + random.nextLong();
    }

    public StopActivityDataObject(JID source, JID initiator, JID user,
        Type type, State state, String stopActivityID) {

        this(source, initiator, user, type, state);
        this.stopActivityID = stopActivityID;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result
            + ((initiator == null) ? 0 : initiator.hashCode());
        result = prime * result + ((state == null) ? 0 : state.hashCode());
        result = prime * result
            + ((stopActivityID == null) ? 0 : stopActivityID.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((user == null) ? 0 : user.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        StopActivityDataObject other = (StopActivityDataObject) obj;
        if (initiator == null) {
            if (other.initiator != null)
                return false;
        } else if (!initiator.equals(other.initiator))
            return false;
        if (state == null) {
            if (other.state != null)
                return false;
        } else if (!state.equals(other.state))
            return false;
        if (stopActivityID == null) {
            if (other.stopActivityID != null)
                return false;
        } else if (!stopActivityID.equals(other.stopActivityID))
            return false;
        if (type == null) {
            if (other.type != null)
                return false;
        } else if (!type.equals(other.type))
            return false;
        if (user == null) {
            if (other.user != null)
                return false;
        } else if (!user.equals(other.user))
            return false;
        return true;
    }

    /**
     * The user to be locked/unlocked by this activityDataObject
     */
    public JID getUser() {
        return user;
    }

    /**
     * The user who requested the lock/unlock.
     * 
     * (in most cases this should be the host)
     */
    public JID getInitiator() {
        return initiator;
    }

    /**
     * Returns the JID of the user to which this StopActivityDataObject should
     * be sent.
     * 
     * This method is a convenience method for getting the user or initiator
     * based on the state of this stop activityDataObject.
     */
    public JID getRecipient() {
        switch (getState()) {
        case INITIATED:
            return getUser();
        case ACKNOWLEDGED:
            return getInitiator();
        default:
            throw new IllegalStateException(
                "StopActivityDataObject is in an illegal state to return a recipient");
        }
    }

    public State getState() {
        return state;
    }

    public StopActivityDataObject generateAcknowledgment(JID source) {
        return new StopActivityDataObject(source, initiator, user, type,
            State.ACKNOWLEDGED, stopActivityID);
    }

    public Type getType() {
        return type;
    }

    public String getActivityID() {
        return stopActivityID;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("StopActivityDataObject (id: " + stopActivityID);
        sb.append(", type: " + type);
        sb.append(", state: " + state);
        sb.append(", initiator: " + initiator.toString());
        sb.append(", affected user: " + user.toString());
        sb.append(", src: " + getSource() + ")");
        return sb.toString();
    }

    public boolean dispatch(IActivityDataObjectConsumer consumer) {
        return consumer.consume(this);
    }

    public void dispatch(IActivityDataObjectReceiver receiver) {
        receiver.receive(this);
    }
}