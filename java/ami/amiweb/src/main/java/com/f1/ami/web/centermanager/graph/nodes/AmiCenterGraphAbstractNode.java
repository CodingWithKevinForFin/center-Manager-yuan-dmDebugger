package com.f1.ami.web.centermanager.graph.nodes;

import com.f1.ami.amicommon.AmiUtils;
import com.f1.ami.web.centermanager.AmiCenterManagerUtils;
import com.f1.ami.web.centermanager.graph.AmiWebCenterGraphManager;


public class AmiCenterGraphAbstractNode implements AmiCenterGraphNode {
	protected boolean fromExternalDs = false;
	protected String externalDsName;
	protected String label;
	protected boolean readOnly = false;
	final private AmiWebCenterGraphManager manager;
	final private long uid;
	private byte type;

	public AmiCenterGraphAbstractNode(AmiWebCenterGraphManager manager, long uid, String label) {
		this.manager = manager;
		this.uid = uid;
		this.label = label;
	}
	
	public AmiCenterGraphAbstractNode(AmiWebCenterGraphManager manager, long uid, String label, String externalDsName) {
		this.manager = manager;
		this.uid = uid;
		this.label = externalDsName + "." + label;
		this.externalDsName = externalDsName;
		this.fromExternalDs = true;
	}
	
	@Override
	public boolean isFromExternalDs() {
		return this.fromExternalDs;
	}
	
	@Override
	public String getExternalDsName() {
		return this.externalDsName;
	}

	@Override
	public String getLabel() {
		return this.label;
	}
	
	@Override
	public String getEscapedLabel() {
		return AmiUtils.escapeVarName(label);
	}

	public void setLabel(String label) {
		this.label = label;
	}

	@Override
	public long getUid() {
		return this.uid;
	}

	@Override
	public byte getType() {
		return type;
	}

	@Override
	public boolean isReadonly() {
		return false;
	}

	public void setReadonly(boolean readonly) {
		this.readOnly = readonly;
	}

	@Override
	public String toString() {
		return AmiCenterManagerUtils.toCenterObjectString(getType(), true) + "::" + this.label;
	}

}
