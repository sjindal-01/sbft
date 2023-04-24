package sbft.consensus.roles;

import sbft.communication.ServerCommunicationSystem;
import sbft.consensus.Consensus;
import sbft.consensus.Epoch;
import sbft.consensus.messages.ConsensusMessage;
import sbft.consensus.messages.KeyShareMessage;
import sbft.consensus.messages.MessageFactory;
import sbft.reconfiguration.ServerViewController;
import sbft.consensus.messages.SigShareMessage;
import sbft.tom.util.thresholdsig.Thresig;
import sbft.tom.core.ExecutionManager;
import sbft.tom.core.TOMLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.concurrent.locks.ReentrantLock;


/**
 * @Author Moonk
 * @Date 2022/4/21
 */
public class Backup {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private int me;
    private ExecutionManager executionManager;
    private MessageFactory factory; // Factory for PaW messages
    private ServerCommunicationSystem communication; // Replicas comunication system
    private TOMLayer tomLayer; // TOM layer
    private ServerViewController controller;
    private BigInteger secret;
    private int keyId;
    private BigInteger verifier;
    private BigInteger groupVerifier;
    private BigInteger n;
    private int l;
    private int cid;
    private BigInteger e;
    public ReentrantLock lock = new ReentrantLock();

    public Backup(ServerCommunicationSystem communication, MessageFactory factory, ServerViewController controller) {
        this.communication = communication;
        this.factory = factory;
        this.controller = controller;
        this.me = controller.getStaticConf().getProcessId();
    }
    public void setExecutionManager(ExecutionManager executionManager) {
        this.executionManager = executionManager;
    }
    public void setTOMLayer(TOMLayer tomLayer) {
        this.tomLayer = tomLayer;
    }
    public void deliver(ConsensusMessage msg){
        if (executionManager.checkLimits(msg)) {
            logger.debug("Processing paxos msg with id " + msg.getNumber());
            processMessage(msg);
        } else {
            logger.debug("Out of context msg with id " + msg.getNumber());
            tomLayer.processOutOfContext();
        }

    }
    public void processMessage(ConsensusMessage msg){
        Consensus consensus = executionManager.getConsensus(msg.getNumber());
        consensus.lock.lock();
        Epoch epoch = consensus.getEpoch(msg.getEpoch(), controller);
        switch (msg.getType()){
            case MessageFactory.PREPARE:{
                PrepareReceived(epoch,msg);
            }break;
            case MessageFactory.FULLCOMMIT:{
                FullCommitReceived(epoch,msg);
            }break;
            case MessageFactory.FULLEXECUTE:{
                FullExcuteReceived(epoch,msg);
            }break;
            case MessageFactory.KEYSHARE:{
                setKeyShare((KeyShareMessage) msg);
            }break;
        }
        consensus.lock.unlock();
    }

    private void PrepareReceived(Epoch epoch, ConsensusMessage msg) {
        if(epoch.propValue == null) {
            epoch.propValue = msg.getValue();
            epoch.propValueHash = tomLayer.computeHash(msg.getValue());
            epoch.getConsensus().addWritten(msg.getValue());
            epoch.deserializedPropValue = tomLayer.checkProposedValue(msg.getValue(), true);
            epoch.getConsensus().getDecision().firstMessageProposed = epoch.deserializedPropValue[0];
        }
        cid = msg.getNumber();
        if(executionManager.getCommitCollector()==me||executionManager.getExecutionCollector()==me){
            executionManager.getPCECollector().setEandN(msg.getValue(),e,n);//set public key
            executionManager.getPCECollector().setCollectorInit(cid);
        }

        SigShareMessage sign = Thresig.sign(msg.getValue(), keyId, n, groupVerifier, verifier, secret, l,cid,epoch.getTimestamp(),MessageFactory.COMMIT, controller.getStaticConf().getProcessId());
        sign.setSender(me);
        communication.send(new int[]{executionManager.getCommitCollector()}, sign);
//        executionManager.processOutOfContext(epoch.getConsensus());
    }
    private void FullCommitReceived(Epoch epoch, ConsensusMessage msg) {
        cid = msg.getNumber();
//        if(executionManager.getExecutionCollector()==me){
//            executionManager.getPCECollector().setEandN(msg.getValue(),e,n);//set public key
//            executionManager.getPCECollector().setCollectorInit(cid);
//        }
        SigShareMessage sign = Thresig.sign(msg.getValue(), keyId, n, groupVerifier, verifier, secret, l,cid,epoch.getTimestamp(),MessageFactory.EXECUTE, controller.getStaticConf().getProcessId());
        sign.setSender(me);
        communication.send(new int[]{executionManager.getExecutionCollector()}, sign);
//        executionManager.processOutOfContext(epoch.getConsensus());
    }

    private void FullExcuteReceived(Epoch epoch, ConsensusMessage msg) {
        decide(epoch);
    }

    private void decide(Epoch epoch) {

//        dec.setDecisionEpoch(epoch);
        epoch.getConsensus().decided(epoch, true);
    }

    public void setKeyShare(KeyShareMessage keyShare){
        this.secret = keyShare.getSecret();
        this.keyId = keyShare.getId();
        this.groupVerifier = keyShare.getGroupVerifier();
        this.n = keyShare.getN();
        this.e = keyShare.getE();
        this.verifier = keyShare.getVerifier();
        this.l = keyShare.getL();
    }

}
