/**
 Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package sbft.communication;

import sbft.consensus.messages.ConsensusMessage;
import sbft.consensus.messages.KeyShareMessage;
import sbft.consensus.messages.MessageFactory;
import sbft.consensus.messages.SigShareMessage;
import sbft.consensus.roles.Backup;
import sbft.consensus.roles.PCECollector;
import sbft.statemanagement.SMMessage;
import sbft.tom.core.TOMLayer;
import sbft.tom.core.messages.TOMMessage;
import sbft.tom.core.messages.ForwardedMessage;
import sbft.tom.leaderchange.LCMessage;
import sbft.tom.util.TOMUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author edualchieri
 */
public class MessageHandler {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Backup backup;
    private PCECollector primary;
    private TOMLayer tomLayer;
    private Mac mac;

    public MessageHandler() {
        try {
            this.mac = TOMUtil.getMacFactory();
        } catch (NoSuchAlgorithmException /*| NoSuchPaddingException*/ ex) {
            System.out.println("Failed to create MAC engine"+ex);
        }
    }
    public void setBackup(Backup backup) {
        this.backup = backup;
    }
    public void setPrimary(PCECollector primary){
        this.primary = primary;
    }

    public void setTOMLayer(TOMLayer tomLayer) {
        this.tomLayer = tomLayer;
    }

    @SuppressWarnings("unchecked")
    protected void processData(SystemMessage sm) {
        if (sm instanceof ConsensusMessage) {
            ConsensusMessage msg = (ConsensusMessage) sm;
            int myId = tomLayer.controller.getStaticConf().getProcessId();
            if (msg.authenticated || msg.getSender() == myId) {
                switch (msg.getType()) {
                    case MessageFactory.COMMIT:
                    case MessageFactory.EXECUTE:{
                        primary.deliver(msg);
                    }break;
                    case MessageFactory.PREPARE:
                    case MessageFactory.FULLCOMMIT:
                    case MessageFactory.FULLEXECUTE:
                    case MessageFactory.KEYSHARE:{
                        backup.deliver(msg);
                    }break;
                }
            }

        }else{
            if (tomLayer.controller.getStaticConf().getUseMACs() == 0 || sm.authenticated) {
                /*** This is Joao's code, related to leader change */
                if (sm instanceof LCMessage) {
                    LCMessage lcMsg = (LCMessage) sm;

                    String type = null;
                    switch(lcMsg.getType()) {

                        case TOMUtil.STOP:
                            type = "STOP";
                            break;
                        case TOMUtil.STOPDATA:
                            type = "STOPDATA";
                            break;
                        case TOMUtil.SYNC:
                            type = "SYNC";
                            break;
                        default:
                            type = "LOCAL";
                            break;
                    }

                    if (lcMsg.getReg() != -1 && lcMsg.getSender() != -1)
                        System.out.println("Received leader change message of type "+type+" for regency "+lcMsg.getReg()+" from replica "+lcMsg.getSender()+"" );
                    else logger.debug("Received leader change message from myself");
                    if (lcMsg.TRIGGER_LC_LOCALLY) tomLayer.requestsTimer.run_lc_protocol();
                    else tomLayer.getSynchronizer().deliverTimeoutRequest(lcMsg);
                    /**************************************************************/

                } else if (sm instanceof ForwardedMessage) {
                    TOMMessage request = ((ForwardedMessage) sm).getRequest();
                    tomLayer.requestReceived(request);

                    /** This is Joao's code, to handle state transfer */
                } else if (sm instanceof SMMessage) {
                    SMMessage smsg = (SMMessage) sm;
                    // System.out.println("(MessageHandler.processData) SM_MSG received: type " + smsg.getType() + ", regency " + smsg.getRegency() + ", (replica " + smsg.getSender() + ")");
                    switch(smsg.getType()) {
                        case TOMUtil.SM_REQUEST:
                            tomLayer.getStateManager().SMRequestDeliver(smsg, tomLayer.controller.getStaticConf().isBFT());
                            break;
                        case TOMUtil.SM_REPLY:
                            tomLayer.getStateManager().SMReplyDeliver(smsg, tomLayer.controller.getStaticConf().isBFT());
                            break;
                        case TOMUtil.SM_ASK_INITIAL:
                            tomLayer.getStateManager().currentConsensusIdAsked(smsg.getSender());
                            break;
                        case TOMUtil.SM_REPLY_INITIAL:
                            tomLayer.getStateManager().currentConsensusIdReceived(smsg);
                            break;
                        default:
                            tomLayer.getStateManager().stateTimeout();
                            break;
                    }
                    /******************************************************************/
                }

                else {
                    logger.warn("UNKNOWN MESSAGE TYPE: " + sm);
                }
            } else {
                logger.warn("Discarding unauthenticated message from " + sm.getSender());
            }
        }
    }

    protected void verifyPending() {
        tomLayer.processOutOfContext();
    }
}
