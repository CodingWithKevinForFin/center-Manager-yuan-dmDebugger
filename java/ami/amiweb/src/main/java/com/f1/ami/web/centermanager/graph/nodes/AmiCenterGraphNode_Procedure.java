package com.f1.ami.web.centermanager.graph.nodes;

import com.f1.ami.web.centermanager.graph.AmiWebCenterGraphManager;

public class AmiCenterGraphNode_Procedure extends AmiCenterGraphAbstractNode {
	public static final byte CODE = AmiCenterGraphNode.TYPE_PROCEDURE;

	public AmiCenterGraphNode_Procedure(AmiWebCenterGraphManager manager, long uid, String label) {
		super(manager, uid, label);
	}

	public AmiCenterGraphNode_Procedure(AmiWebCenterGraphManager manager, long uid, String label, boolean readOnly) {
		this(manager, uid, label);
		this.readOnly = readOnly;
	}

	@Override
	public byte getType() {
		return CODE;
	}

}
