package com.f1.ami.web.centermanager.portlets;

import java.util.Map;

import com.f1.ami.portlets.AmiWebHeaderPortlet;
import com.f1.suite.web.portal.PortletConfig;
import com.f1.suite.web.portal.impl.GridPortlet;
import com.f1.suite.web.portal.impl.HtmlPortlet;
import com.f1.suite.web.portal.impl.HtmlPortlet.Callback;
import com.f1.suite.web.portal.impl.HtmlPortletListener;
import com.f1.suite.web.portal.impl.form.FormPortlet;
import com.f1.suite.web.portal.impl.form.FormPortletButton;
import com.f1.suite.web.portal.impl.form.FormPortletField;
import com.f1.suite.web.portal.impl.form.FormPortletListener;

public class AmiCenterManagerApplyScriptPortlet extends GridPortlet implements FormPortletListener, HtmlPortletListener {
	final private AmiWebHeaderPortlet header;
	final private AmiCenterManagerReviewApplyScriptPortlet owner;
	final private FormPortlet infoForm;
	final private HtmlPortlet messageLogForm;
	final private FormPortlet buttonsFp;
	final private FormPortletButton backButton;
	final private FormPortletButton finishButton;

	public AmiCenterManagerApplyScriptPortlet(PortletConfig config, AmiCenterManagerReviewApplyScriptPortlet parent) {
		super(config);
		owner = parent;
		header = new AmiWebHeaderPortlet(generateConfig());
		header.setInformationHeaderHeight(70);
		header.updateBlurbPortletLayout("Applying SQL script to AMIDB", null);
		header.setShowSearch(false);
		header.setShowBar(false);
		infoForm = new FormPortlet(generateConfig());

		messageLogForm = new HtmlPortlet(generateConfig());
		messageLogForm.addListener(this);
		messageLogForm.setJavascript("scrollToBottom()");
		messageLogForm.setCssStyle("style.fontFamily=courier|_bg=#000000|_fg=#44FF44|style.overflow=scroll");

		buttonsFp = new FormPortlet(generateConfig());
		buttonsFp.getFormPortletStyle().setLabelsWidth(200);
		buttonsFp.addFormPortletListener(this);
		backButton = buttonsFp.addButton(new FormPortletButton("Back"));
		backButton.setEnabled(false);
		finishButton = buttonsFp.addButton(new FormPortletButton("Finish"));
		finishButton.setEnabled(false);
		addChild(header, 0, 0);
		addChild(infoForm, 0, 1);
		addChild(messageLogForm, 0, 2);
		addChild(buttonsFp, 0, 3);
		setRowSize(3, 50);

	}

	@Override
	public void onButtonPressed(FormPortlet portlet, FormPortletButton button) {
		if (button == this.finishButton) {
			owner.close();
			return;
		} else if (button == this.backButton) {
			owner.backToReviewStage();
			return;
		}
	}

	public void appendHtml(String html) {
		messageLogForm.appendHtml(html);
	}

	@Override
	public void onFieldValueChanged(FormPortlet portlet, FormPortletField<?> field, Map<String, String> attributes) {
	}

	@Override
	public void onSpecialKeyPressed(FormPortlet formPortlet, FormPortletField<?> field, int keycode, int mask, int cursorPosition) {
	}

	@Override
	public void onUserClick(HtmlPortlet portlet) {

	}

	@Override
	public void onUserCallback(HtmlPortlet htmlPortlet, String id, int mouseX, int mouseY, Callback cb) {

	}

	@Override
	public void onHtmlChanged(String old, String nuw) {

	}

	public void enableFinishButton(boolean enabled) {
		finishButton.setEnabled(enabled);
	}

	public void enableBackButton(boolean enabled) {
		backButton.setEnabled(enabled);
	}

}
