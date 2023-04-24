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
package sbft.consensus;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import sbft.consensus.messages.ConsensusMessage;

import sbft.consensus.messages.SigShareMessage;
import sbft.reconfiguration.ServerViewController;
import sbft.reconfiguration.views.View;
import sbft.tom.core.messages.TOMMessage;
import sbft.tom.util.thresholdsig.Thresig;


/**
 * This class stands for a consensus epoch, as described in
 * Cachin's 'Yet Another Visit to Paxos' (April 2011)
 */
public class Epoch implements Serializable {

    private static final long serialVersionUID = -2891450035863688295L;
    private final transient Consensus consensus; // Consensus where the epoch belongs to

    private final int timestamp; // Epochs's timestamp
    private final int me; // Process ID
    private boolean alreadyRemoved = false; // indicates if this epoch was removed from its consensus
    public byte[] propValue = null; // proposed value
    public SigShareMessage[] sigs = null;
    private boolean[] sigsSetted = null;
    public TOMMessage[] deserializedPropValue = null; //utility var
    public byte[] propValueHash = null; // proposed value hash
    public HashSet<SigShareMessage> proof; // threshold signature proof
    private BigInteger n = null;   //threshold signature public key
    private BigInteger e = null;   //threshold signature public key
    private View lastView = null;

    private ServerViewController controller;

    /**
     * Creates a new instance of Epoch for acceptors
     * @param controller
     * @param parent Consensus to which this epoch belongs
     * @param timestamp Timestamp of the epoch
     */
    public Epoch(ServerViewController controller, Consensus parent, int timestamp) {
        this.consensus = parent;
        this.timestamp = timestamp;
        this.controller = controller;
        this.proof = new HashSet<>();
        //ExecutionManager manager = consensus.getManager();

        this.lastView = controller.getCurrentView();
        this.me = controller.getStaticConf().getProcessId();
        int n = controller.getCurrentViewN();
        this.sigs = new SigShareMessage[n];
        this.sigsSetted = new boolean[n];
        Arrays.fill(sigsSetted,false);
    }
            
    /**
     * Set this epoch as removed from its consensus instance
     */
    public void setRemoved() {
        this.alreadyRemoved = true;
    }

    /**
     * Informs if this epoch was removed from its consensus instance
     * @return True if it is removed, false otherwise
     */
    public boolean isRemoved() {
        return this.alreadyRemoved;
    }


    public void addToProof(SigShareMessage pm) {
        proof.add(pm);
    }
    
    public Set<SigShareMessage> getProof() {
        return proof;
    }
    /**
     * Retrieves the duration for the timeout
     * @return Duration for the timeout
     */
    /*public long getTimeout() {
        return this.timeout;
    }*/
    public BigInteger getN() {
        return n;
    }

    public BigInteger getE() {
        return e;
    }
    /**
     * Retrieves this epoch's timestamp
     * @return This epoch's timestamp
     */
    public int getTimestamp() {
        return timestamp;
    }

    /**
     * Retrieves this epoch's consensus
     * @return This epoch's consensus
     */
    public Consensus getConsensus() {
        return consensus;
    }

    public void setLocalSignature(SigShareMessage msg) {
        int p = this.controller.getCurrentViewPos(controller.getCurrentViewPos(msg.getSender()));
        if (p >= 0) {
            sigs[p] = msg;
            sigsSetted[p] = true;
            addToProof(msg);
        }
    }

    /*************************** DEBUG METHODS *******************************/

    public void setThresholdSigPK(BigInteger n, BigInteger e) {
        this.n = n;
        this.e = e;
    }

    public SigShareMessage[] getLocalSignature(int n) {
        SigShareMessage[] sigShareMessages = new SigShareMessage[n];
        int p = 0;
        for (int i = 0; i < sigs.length; i++) {
            if (sigs[i] != null&&sigsSetted[i]) {
                sigShareMessages[p++] = sigs[i];
            }
        }
        return sigShareMessages;
    }


    public int countSignatures(byte[] value, int l, BigInteger n) {
        int count = 0;
        if(n==null){
            return count;
        }
        for (int i = 0; i < sigs.length; i++) {

            if (sigs[i] != null&& Thresig.checkVerifier(value, sigs[i], l, n) ) {//&& ThresholdSignature.checkVerifier(value, sigs[i], l, n)
                sigsSetted[i] = true;
                count++;
            } else {
                sigsSetted[i] = false;
            }
        }
        return count;
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }
    
    /**
     * Clear all epoch info.
     */
    public void clear() {
        int n = controller.getCurrentViewN();
        this.sigsSetted = new boolean[n];
        Arrays.fill(sigsSetted,false);
        this.proof = new HashSet<SigShareMessage>();
        this.sigs = new SigShareMessage[n];
        this.n = null;
        this.e = null;
    }
}
