package com.jq.findapp.entity;

import java.math.BigInteger;

import javax.persistence.Entity;
import javax.persistence.EntityListeners;

import com.jq.findapp.repository.RepositoryListener;

@Entity
@EntityListeners(RepositoryListener.class)
public class ContactVisit extends BaseEntity {
	private Long count;
	private BigInteger contactId;
	private BigInteger contactId2;

	public Long getCount() {
		return count;
	}

	public void setCount(Long count) {
		this.count = count;
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public BigInteger getContactId2() {
		return contactId2;
	}

	public void setContactId2(BigInteger contactId2) {
		this.contactId2 = contactId2;
	}
}
