package com.jq.findapp.repository.listener;

import org.springframework.beans.factory.annotation.Autowired;

import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.service.backend.SurveyService;

public class ContactMarketingListener extends AbstractRepositoryListener<ContactMarketing> {
	@Autowired
	private SurveyService surveyService;

	@Override
	public void postPersist(final ContactMarketing contactMarketing) throws Exception {
		surveyService.updateResult(contactMarketing.getClientMarketingId());
	}

	@Override
	public void postUpdate(final ContactMarketing contactMarketing) throws Exception {
		surveyService.updateResult(contactMarketing.getClientMarketingId());
	}

}
