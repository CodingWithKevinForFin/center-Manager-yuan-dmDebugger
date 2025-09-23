package com.f1.ami.web.centermanager.graph.nodes;

import com.f1.ami.web.centermanager.graph.AmiWebCenterGraphManager;

public class AmiCenterGraphNode_Datasource extends AmiCenterGraphAbstractNode {
	public static final byte CODE = AmiCenterGraphNode.TYPE_DATASOURCE;

	public AmiCenterGraphNode_Datasource(AmiWebCenterGraphManager manager, long uid, String label) {
		super(manager, uid, label);
	}

	@Override
	public byte getType() {
		return CODE;
	}
}
