package com.f1.ami.web.centermanager.graph.nodes;

import com.f1.ami.web.centermanager.graph.AmiWebCenterGraphManager;

public class AmiCenterGraphNode_Index extends AmiCenterGraphAbstractNode {
	public static final byte CODE = AmiCenterGraphNode.TYPE_INDEX;
	private AmiCenterGraphNode_Table bindingTable = null;

	public AmiCenterGraphNode_Index(AmiWebCenterGraphManager manager, long uid, String label) {
		super(manager, uid, label);
	}
	public AmiCenterGraphNode_Index(AmiWebCenterGraphManager manager, long uid, String label, boolean readOnly) {
		this(manager, uid, label);
		this.readOnly = readOnly;
	}

	@Override
	public byte getType() {
		return CODE;
	}

	public void setBindingTable(AmiCenterGraphNode_Table table) {
		this.bindingTable = table;
	}

	public AmiCenterGraphNode_Table getBindingTable() {
		return this.bindingTable;
	}

}
