package com.f1.ami.web.centermanager;

public interface AmiWebCenterEntityListener {
	public void onAmiCenterEntityAdded(String nodeType, String nodeName, Object correlationData, boolean readonly);

	public void onAmiCenterEntityRemoved(String nodeType, String nodeName, Object correlationData);
}
