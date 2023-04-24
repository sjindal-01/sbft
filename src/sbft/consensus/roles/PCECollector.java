package sbft.consensus.roles;

import sbft.communication.ServerCommunicationSystem;
import sbft.consensus.Consensus;
import sbft.consensus.Epoch;
import sbft.consensus.messages.ConsensusMessage;
import sbft.consensus.messages.KeyShareMessage;
import sbft.consensus.messages.MessageFactory;
import sbft.consensus.messages.SigShareMessage;
import sbft.reconfiguration.ServerViewController;
import sbft.tom.util.thresholdsig.Thresig;
import sbft.tom.core.TOMLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class PCECollector {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private int me; // This replica ID
    private MessageFactory factory; // Factory for PaW messages
    private ServerCommunicationSystem communication; // Replicas comunication system
    private TOMLayer tomLayer; // TOM layer
    private ServerViewController controller;
    //private Cipher cipher;
    private byte[] value = null;
    private BigInteger n;
    private BigInteger e;
    private int cid = -1;
    private boolean alreadyCommit = false;
    private ReentrantLock lock = new ReentrantLock();
    public PCECollector(ServerCommunicationSystem communication, MessageFactory factory, ServerViewController controller) {
        this.communication = communication;
        this.factory = factory;
        this.controller = controller;
    }

    public void deliver(ConsensusMessage consensusMessage){
        SigShareMessage msg = (SigShareMessage) consensusMessage;
        Consensus consensus = tomLayer.execManager.getConsensus(msg.getNumber());
        Epoch epoch = consensus.getEpoch(msg.getEpoch(), controller);
        consensus.lock.lock();
        processMessage(msg,epoch);
        consensus.lock.unlock();
    }
    public void processMessage(SigShareMessage msg,Epoch epoch){
        epoch.setLocalSignature(msg);
        int count = epoch.countSignatures(value, controller.getCurrentViewN(), this.n);
        if (count > controller.getQuorum() && !alreadyCommit) {//controller.getQuorum()
            SigShareMessage[] sigsArray = epoch.getLocalSignature(count);
            boolean verify = Thresig.verify(value, sigsArray, count, controller.getCurrentViewN(), this.n, this.e);
            if (verify && !alreadyCommit) {
                epoch.setThresholdSigPK(this.n, this.e);
                logger.debug("Validation succeeded!");
                //Send to GS Leader, upper-layer consensus
                    if(msg.getType()==MessageFactory.COMMIT){
                        communication.send(this.controller.getCurrentViewAcceptors(), factory.createFullCommit(epoch.getConsensus().getId(),0,value));
                    }else if(msg.getType()==MessageFactory.EXECUTE){
                        communication.send(this.controller.getCurrentViewAcceptors(), factory.createFullExecute(epoch.getConsensus().getId(),0,value));
                    }
                alreadyCommit = true;
//                logger.info("Request "+ consensus.getId() +" LS-Prepared done ..." );
            } else if (!tomLayer.isChangingLeader()) {
                logger.debug("verification failed!   need to view change...");
//                    tomLayer.getSynchronizer().triggerTimeout(new LinkedList<>());
            } else {
                logger.debug("enough signatures have been received!");
            }
        }

    }
    public void setCollectorInit(int cid){
        lock.lock();
        this.cid = cid;
        reset();
        lock.unlock();
    }
    public void setTOMLayer(TOMLayer tomLayer){
        this.tomLayer = tomLayer;
    }

    /**
     * start Consensus
     * @param cid
     * @param value
     */
    public void startConsensus(int cid, byte[] value) {
        this.cid = cid;
        this.value = value;
        ConsensusMessage msg = factory.createPrepare(cid, 0, value);
        reset();
        communication.send(this.controller.getCurrentViewAcceptors(), msg);
    }
    public void sendThresholdSigKeys(int execId){
        KeyShareMessage[] keyShareMessages = Thresig.generateKeys(controller.getCurrentViewN() - controller.getCurrentViewF(), controller.getCurrentViewN(), me,execId);
        this.n = keyShareMessages[0].getN();
        this.e = keyShareMessages[0].getE();//public key
        for(int i=0;i<controller.getCurrentViewN();i++){
            keyShareMessages[i].setSender(controller.getStaticConf().getProcessId());
            tomLayer.getCommunication().send(new int[]{i},keyShareMessages[i]);
        }
    }
    public void setEandN(byte[] value,BigInteger e,BigInteger n){
        this.value = value;
        this.e = e;
        this.n = n;
    }


    public void reset(){
        alreadyCommit = false;
    }
}
