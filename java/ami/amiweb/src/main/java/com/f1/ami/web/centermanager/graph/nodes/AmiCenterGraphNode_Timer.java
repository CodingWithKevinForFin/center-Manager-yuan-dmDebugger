package com.f1.ami.web.centermanager.graph.nodes;

import com.f1.ami.web.centermanager.graph.AmiWebCenterGraphManager;

public class AmiCenterGraphNode_Timer extends AmiCenterGraphAbstractNode {
	public static final byte CODE = AmiCenterGraphNode.TYPE_TIMER;

	public AmiCenterGraphNode_Timer(AmiWebCenterGraphManager manager, long uid, String label) {
		super(manager, uid, label);
	}
	
	public AmiCenterGraphNode_Timer(AmiWebCenterGraphManager manager, long uid, String label, String externalDsName) {
		super(manager, uid, label, externalDsName);
	}
	
	public AmiCenterGraphNode_Timer(AmiWebCenterGraphManager manager, long uid, String label, boolean readOnly, String externalDsName) {
		this(manager, uid, label, externalDsName);
		this.readOnly = readOnly;
	}
	

	public AmiCenterGraphNode_Timer(AmiWebCenterGraphManager manager, long uid, String label, boolean readOnly) {
		this(manager, uid, label);
		this.readOnly = readOnly;
	}

	@Override
	public byte getType() {
		return CODE;
	}

}
