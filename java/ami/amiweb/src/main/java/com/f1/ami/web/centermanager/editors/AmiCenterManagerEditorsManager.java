package com.f1.ami.web.centermanager.editors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.centermanager.editors.AmiCenterManagerRichTableEditorPortlet;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Procedure;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Table;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Timer;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode_Trigger;

import com.f1.suite.web.portal.Portlet;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.PortletHelper;
import com.f1.suite.web.portal.impl.DesktopPortlet.Window;

public class AmiCenterManagerEditorsManager {
	private final AmiWebService service;
	//table editors are separate from other editors
	private Map<String, AmiCenterManagerAbstractEditCenterObjectPortlet> editorsByPortletId;
	private Map<String, AmiCenterManagerRichTableEditorPortlet> tableEditorsByPortletId;

	public AmiCenterManagerEditorsManager(AmiWebService service) {
		this.service = service;
		this.editorsByPortletId = new HashMap<String, AmiCenterManagerAbstractEditCenterObjectPortlet>();
		this.tableEditorsByPortletId = new HashMap<String, AmiCenterManagerRichTableEditorPortlet>();
	}
	private PortletConfig generateConfig() {
		return this.service.getPortletManager().generateConfig();
	}

	public Portlet getEditorByPortletId(String id) {
		return this.editorsByPortletId.get(id) == null ? tableEditorsByPortletId.get(id) : this.editorsByPortletId.get(id);
	}

	public int getEditorsCount() {
		return this.editorsByPortletId.size() + tableEditorsByPortletId.size();
	}

	public Set<String> getEditorsPortletIds() {
		Set<String> editorsIds = new HashSet<>(this.editorsByPortletId.keySet());
		editorsIds.addAll(this.tableEditorsByPortletId.keySet());
		return editorsIds;
	}

	public AmiCenterManagerRichTableEditorPortlet showEditTablePortlet(String sql, AmiCenterGraphNode_Table node) {
		for (AmiCenterManagerRichTableEditorPortlet i : this.tableEditorsByPortletId.values()) {
			if (i.getCorrelationNode() == node) {
				PortletHelper.ensureVisible(i);
				return i;
			}
		}
		AmiCenterManagerRichTableEditorPortlet editor = new AmiCenterManagerRichTableEditorPortlet(generateConfig(), sql, node, false);
		String portletId = editor.getPortletId();
		Window w = this.service.getDesktop().getDesktop().addChild("Edit Table", editor);

		this.service.getDesktop().applyEditModeStyle(w, 1200, 1250);
		this.service.getPortletManager().onPortletAdded(editor);

		this.tableEditorsByPortletId.put(portletId, editor);
		return editor;
	}

	public AmiCenterManagerRichTableEditorPortlet showAddTablePortlet() {
		AmiCenterManagerRichTableEditorPortlet editor = new AmiCenterManagerRichTableEditorPortlet(generateConfig(), true);
		Window w = this.service.getDesktop().getDesktop().addChild("Edit Table", editor);

		this.service.getDesktop().applyEditModeStyle(w, 1200, 1250);
		this.service.getPortletManager().onPortletAdded(editor);
		String portletId = editor.getPortletId();
		this.tableEditorsByPortletId.put(portletId, editor);
		return editor;
	}

	public AmiCenterManagerAbstractEditCenterObjectPortlet showEditCenterObjectPortlet(String sql, AmiCenterGraphNode node) {
		for (AmiCenterManagerAbstractEditCenterObjectPortlet i : this.editorsByPortletId.values()) {
			if (i.getCorrelationNode() == node) {
				PortletHelper.ensureVisible(i);
				return i;
			}
		}
		AmiCenterManagerAbstractEditCenterObjectPortlet editor = null;
		String target = null;
		switch (node.getType()) {
			case AmiCenterGraphNode_Trigger.CODE:
				editor = new AmiCenterManagerEditTriggerPortlet(generateConfig(), sql, (AmiCenterGraphNode_Trigger) node);
				target = "Trigger";
				break;
			case AmiCenterGraphNode_Timer.CODE:
				editor = new AmiCenterManagerEditTimerPortlet(generateConfig(), sql, (AmiCenterGraphNode_Timer) node);
				target = "Timer";
				break;
			case AmiCenterGraphNode_Procedure.CODE:
				editor = new AmiCenterManagerEditProcedurePortlet(generateConfig(), sql, (AmiCenterGraphNode_Procedure) node);
				target = "Procedure";
				break;
			default:
				throw new NullPointerException();
		}
		String portletId = editor.getPortletId();
		Window w = this.service.getDesktop().getDesktop().addChild("Edit " + target, editor);

		this.service.getDesktop().applyEditModeStyle(w, 800, 850);
		this.service.getPortletManager().onPortletAdded(editor);

		this.editorsByPortletId.put(portletId, editor);
		return editor;
	}

	public AmiCenterManagerAbstractEditCenterObjectPortlet showAddCenterObjectPortlet(byte nodeType) {
		AmiCenterManagerAbstractEditCenterObjectPortlet editor = null;
		String target = null;
		switch (nodeType) {
			case AmiCenterGraphNode_Trigger.CODE:
				editor = new AmiCenterManagerEditTriggerPortlet(generateConfig(), true);
				target = "Trigger";
				break;
			case AmiCenterGraphNode_Timer.CODE:
				editor = new AmiCenterManagerEditTimerPortlet(generateConfig(), true);
				target = "Timer";
				break;
			case AmiCenterGraphNode_Procedure.CODE:
				editor = new AmiCenterManagerEditProcedurePortlet(generateConfig(), true);
				target = "Procedure";
				break;
			default:
				throw new NullPointerException();
		}
		Window w = this.service.getDesktop().getDesktop().addChild("Add " + target, editor);
		this.service.getDesktop().applyEditModeStyle(w, 800, 850);
		this.service.getPortletManager().onPortletAdded(editor);

		String portletId = editor.getPortletId();
		this.editorsByPortletId.put(portletId, editor);
		return editor;
	}

	public void onPortletClosed(AmiCenterManagerAbstractEditCenterObjectPortlet oldPortlet) {
		this.removeEditor(oldPortlet);
	}

	public void onPortletClosed(AmiCenterManagerRichTableEditorPortlet oldPortlet) {
		this.removeTableEditor(oldPortlet);
	}

	private void removeEditor(AmiCenterManagerAbstractEditCenterObjectPortlet wiz) {
		String portletId = wiz.getPortletId();
		if (this.editorsByPortletId.containsKey(portletId))
			this.editorsByPortletId.remove(portletId);
	}

	private void removeTableEditor(AmiCenterManagerRichTableEditorPortlet wiz) {
		String portletId = wiz.getPortletId();
		if (this.tableEditorsByPortletId.containsKey(portletId))
			this.tableEditorsByPortletId.remove(portletId);
	}

}
