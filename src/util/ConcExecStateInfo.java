package util;

import java.io.Serializable;

public class ConcExecStateInfo implements Serializable {
	
	int stateID;
	int threadID;
	int instructionsExecutedInState;
	
	public ConcExecStateInfo() {
		
	}
	
	public ConcExecStateInfo(int stateID, int nextThreadID, int instructionsExecutedInState) {
		this.stateID = stateID;
		this.threadID = nextThreadID;
		this.instructionsExecutedInState = instructionsExecutedInState;
	}
	
	public int getStateID() {
		return stateID;
	}
	public void setStateID(int stateID) {
		this.stateID = stateID;
	}
	public int getThreadID() {
		return threadID;
	}
	public void setThreadID(int threadID) {
		this.threadID = threadID;
	}
	public int getInstructionsExecutedInState() {
		return instructionsExecutedInState;
	}
	public void setInstructionsExecutedInState(int instructionsExecutedInState) {
		this.instructionsExecutedInState = instructionsExecutedInState;
	}
}