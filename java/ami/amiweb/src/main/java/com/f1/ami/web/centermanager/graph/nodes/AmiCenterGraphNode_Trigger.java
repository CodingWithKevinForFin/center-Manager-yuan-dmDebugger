package com.f1.ami.web.centermanager.graph.nodes;

import java.util.LinkedHashSet;
import java.util.Set;

import com.f1.ami.web.centermanager.graph.AmiWebCenterGraphManager;


public class AmiCenterGraphNode_Trigger extends AmiCenterGraphAbstractNode {

	public static final byte CODE = AmiCenterGraphNode.TYPE_TRIGGER;
	private Set<AmiCenterGraphNode_Table> bindingTables = new LinkedHashSet<AmiCenterGraphNode_Table>();

	//add
	private Set<AmiCenterGraphNode_Table> sourceTables = new LinkedHashSet<AmiCenterGraphNode_Table>();
	private Set<AmiCenterGraphNode_Table> sinkTables = new LinkedHashSet<AmiCenterGraphNode_Table>();
	//TODO: add triggertype
	private short triggerType = -1;

	public AmiCenterGraphNode_Trigger(AmiWebCenterGraphManager manager, long uid, String label) {
		super(manager, uid, label);
	}

	public AmiCenterGraphNode_Trigger(AmiWebCenterGraphManager manager, long uid, String label, boolean readOnly) {
		this(manager, uid, label);
		this.readOnly = readOnly;
	}

	@Override
	public byte getType() {
		return CODE;
	}

	public void setBindingTable(AmiCenterGraphNode_Table t) {
		this.bindingTables.add(t);
	}

	public Set<AmiCenterGraphNode_Table> getBindingTables() {
		return this.bindingTables;
	}

	public void addSourceTable(AmiCenterGraphNode_Table t) {
		this.sourceTables.add(t);
	}

	public void addSinkTable(AmiCenterGraphNode_Table t) {
		this.sinkTables.add(t);
	}

	public Set<AmiCenterGraphNode_Table> getSourceTables() {
		return this.sourceTables;
	}

	public Set<AmiCenterGraphNode_Table> getSinkTables() {
		return this.sinkTables;
	}

	public AmiCenterGraphNode_Table getSingletonSinkTable() {
		return this.sinkTables.iterator().next();
	}

	public short getTriggerType() {
		return this.triggerType;
	}

	public void setTriggerType(short toSet) {
		this.triggerType = toSet;
	}

}
