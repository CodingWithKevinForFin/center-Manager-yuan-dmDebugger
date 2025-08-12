package com.f1.ami.web.centermanager.graph;

import java.util.List;
import java.util.Map;

import com.f1.ami.web.AmiWebService;
import com.f1.ami.web.centermanager.graph.nodes.AmiCenterGraphNode;
import com.f1.ami.web.dm.AmiWebDm;
import com.f1.base.Action;
import com.f1.container.ResultMessage;
import com.f1.suite.web.menu.impl.BasicWebMenu;
import com.f1.suite.web.menu.impl.BasicWebMenuLink;
import com.f1.suite.web.portal.BackendResponseListener;
import com.f1.suite.web.portal.PortletManager;
import com.f1.suite.web.portal.impl.ConfirmDialog;
import com.f1.suite.web.portal.impl.ConfirmDialogListener;
import com.f1.suite.web.portal.impl.ConfirmDialogPortlet;

public class AmiCenterManagerSmartGraphMenu {

	public static BasicWebMenu createContextMenu(AmiWebService service, List<AmiCenterGraphNode> selectedNodesList, boolean allowModification) {
		BasicWebMenu menu = new BasicWebMenu();
		int selectedCount = selectedNodesList.size();
		//switch on selected cnt
		if (selectedCount == 1) {
			AmiCenterGraphNode data = selectedNodesList.get(0);
			byte type = data.getType();
			if (AmiCenterGraphNode.TYPE_TABLE == type) {
				if (allowModification) {
					menu.addChild(new BasicWebMenuLink("Add Table", true, "add_table"));
					menu.addChild(new BasicWebMenuLink("Edit Table", true, "edit_table"));
					menu.addChild(new BasicWebMenuLink("Delete Table", true, "delete_table"));
				}
			} else if (AmiCenterGraphNode.TYPE_TRIGGER == type) {
				if (allowModification) {
					menu.addChild(new BasicWebMenuLink("Add Trigger", true, "add_trigger"));
					menu.addChild(new BasicWebMenuLink("Edit Trigger", true, "edit_trigger"));
					menu.addChild(new BasicWebMenuLink("Delete Trigger", true, "delete_trigger"));
				}
			} else if (AmiCenterGraphNode.TYPE_TIMER == type) {
				if (allowModification) {
					menu.addChild(new BasicWebMenuLink("Add Timer", true, "add_timer"));
					menu.addChild(new BasicWebMenuLink("Edit Timer", true, "edit_timer"));
					menu.addChild(new BasicWebMenuLink("Delete Timer", true, "delete_timer"));
				}
			} else if (AmiCenterGraphNode.TYPE_PROCEDURE == type) {
				if (allowModification) {
					menu.addChild(new BasicWebMenuLink("Add Procedure", true, "add_procedure"));
					menu.addChild(new BasicWebMenuLink("Edit Procedure", true, "edit_procedure"));
					menu.addChild(new BasicWebMenuLink("Delete Procedure", true, "delete_procedure"));
				}
			} else if (AmiCenterGraphNode.TYPE_METHOD == type) {
				if (allowModification) {
					menu.addChild(new BasicWebMenuLink("Add Method", true, "add_method"));
					menu.addChild(new BasicWebMenuLink("Edit Method", true, "edit_method"));
					menu.addChild(new BasicWebMenuLink("Delete Method", true, "delete_method"));
				}
			} else if (AmiCenterGraphNode.TYPE_INDEX == type) {
				if (allowModification) {
					menu.addChild(new BasicWebMenuLink("Add Index", true, "add_index"));
					menu.addChild(new BasicWebMenuLink("Edit Index", true, "edit_index"));
					menu.addChild(new BasicWebMenuLink("Delete Index", true, "delete_index"));
				}
			} else if (AmiCenterGraphNode.TYPE_DBO == type) {
				if (allowModification) {
					menu.addChild(new BasicWebMenuLink("Add Dbo", true, "add_Dbo"));
					menu.addChild(new BasicWebMenuLink("Edit Dbo", true, "edit_Dbo"));
					menu.addChild(new BasicWebMenuLink("Delete Dbo", true, "delete_Dbo"));
				}
			}
			return menu;
		} else if (selectedCount > 1 && allowModification) {
			boolean isNodeTypeSame = isAllNodeTypeSame(selectedNodesList);
			if (isNodeTypeSame) {//TODO:Also check isAllNodeModifiable
				switch (selectedNodesList.get(0).getType()) {
					case AmiCenterGraphNode.TYPE_TABLE:
						menu.addChild(new BasicWebMenuLink("Delete Table", true, "delete_table"));
						break;
					case AmiCenterGraphNode.TYPE_TRIGGER:
						menu.addChild(new BasicWebMenuLink("Delete Trigger", true, "delete_trigger"));
						break;
					case AmiCenterGraphNode.TYPE_TIMER:
						menu.addChild(new BasicWebMenuLink("Delete Timer", true, "delete_timer"));
						break;
					case AmiCenterGraphNode.TYPE_PROCEDURE:
						menu.addChild(new BasicWebMenuLink("Delete Procedure", true, "delete_procedure"));
						break;
					case AmiCenterGraphNode.TYPE_METHOD:
						menu.addChild(new BasicWebMenuLink("Delete Method", true, "delete_method"));
						break;
					case AmiCenterGraphNode.TYPE_DBO:
						menu.addChild(new BasicWebMenuLink("Delete DBO", true, "delete_dbo"));
						break;
					case AmiCenterGraphNode.TYPE_INDEX:
						menu.addChild(new BasicWebMenuLink("Delete Index", true, "delete_index"));
						break;
					default:
						menu.addChild(new BasicWebMenuLink("Unknown Node Type", false, "unknown_action"));
						break;
				}
				return menu;
			}

		}
		return null;
	}

	//manages all the add_xxx action because it doesn't need to enage with the backend
	static public void onMenuItemAddAction(AmiWebService service, String id) {
		PortletManager manager = service.getPortletManager();
		if ("add_table".equals(id)) {
			service.getAmiCenterManagerEditorsManager().showAddTablePortlet();
		} else if ("add_trigger".equals(id)) {
			service.getAmiCenterManagerEditorsManager().showAddCenterObjectPortlet(AmiCenterGraphNode.TYPE_TRIGGER);
		} else if ("add_timer".equals(id)) {
			service.getAmiCenterManagerEditorsManager().showAddCenterObjectPortlet(AmiCenterGraphNode.TYPE_TIMER);
		} else if ("add_procedure".equals(id)) {
			service.getAmiCenterManagerEditorsManager().showAddCenterObjectPortlet(AmiCenterGraphNode.TYPE_PROCEDURE);
		} else if ("add_index".equals(id)) {
			//manager.showDialog("Add Index", new AmiCenterManagerAddIndexPortlet(manager.generateConfig()), 500, 550);
		} else if ("add_method".equals(id)) {
			//manager.showDialog("Add Method", new AmiCenterManagerEditMethodPortlet(manager.generateConfig(), true), 1000, 1450);
		} else if ("add_dbo".equals(id)) {
			//manager.showDialog("Add Dbo", new AmiCenterManagerAddDboPortlet(manager.generateConfig()), 500, 550);
		}
	}

	public static boolean isAllNodeTypeSame(List<AmiCenterGraphNode> selectedNodesList) {
		if (selectedNodesList == null || selectedNodesList.size() < 2)
			throw new IllegalArgumentException("node list size needs to be at least 2");
		byte firstNodeType = selectedNodesList.get(0).getType();
		for (AmiCenterGraphNode data : selectedNodesList) {
			if (data.getType() != firstNodeType)
				return false;
		}
		return true;
	}

	public static class DialogListener implements ConfirmDialogListener, BackendResponseListener {

		final private String id;
		final private AmiWebService service;
		final private List<AmiCenterGraphNode> nodes;

		public DialogListener(AmiWebService service, String id, List<AmiCenterGraphNode> nodes) {
			this.id = id;
			this.service = service;
			this.nodes = nodes;
		}

		@Override
		public boolean onButton(ConfirmDialog source, String id) {
			PortletManager manager = service.getPortletManager();
			if (ConfirmDialogPortlet.ID_YES.equals(id)) {
				if ("DELETE_TRIGGER".equals(source.getCallback())) {
					Map<String, AmiWebDm> todelete = (Map<String, AmiWebDm>) source.getCorrelationData();
					for (String s : todelete.keySet())
						this.service.getDmManager().removeDm(s);
				}
			}
			return false;
		}

		@Override
		public void onBackendResponse(ResultMessage<Action> result) {
			// TODO Auto-generated method stub

		}
	}
}
