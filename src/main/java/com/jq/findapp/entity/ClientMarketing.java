package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Text.TextId;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

@Entity
public class ClientMarketing extends BaseEntity {
	private BigInteger clientId;
	private Boolean createResult;
	private Boolean share = true;
	private String age;
	private String gender;
	private String language;
	private String publishId;
	private String region;
	private String skills;
	private String storage;
	private String image;
	private Timestamp endDate;
	private Timestamp startDate;

	public static class Poll {
		public String html;
		public String type;
		public String prolog;
		public String epilog;
		public String subject;
		public String locationPrefix;
		public String locationSearch;
		public String publishingPostfix;
		public TextId textId;
		public final List<Question> questions = new ArrayList<>();
	}

	public static class Question {
		public String id;
		public String question;
		public String textField;
		public String preset;
		public Boolean multiple;
		public final List<Answer> answers = new ArrayList<>();
	}

	public static class Answer {
		public String answer;
		public String key;
		public String next;
	}

	public BigInteger getClientId() {
		return clientId;
	}

	public void setClientId(final BigInteger clientId) {
		this.clientId = clientId;
	}

	public boolean getCreateResult() {
		return createResult;
	}

	public void setCreateResult(boolean createResult) {
		this.createResult = createResult;
	}

	public String getPublishId() {
		return publishId;
	}

	public void setPublishId(String publishId) {
		this.publishId = publishId;
	}

	public Timestamp getStartDate() {
		return startDate;
	}

	public void setStartDate(final Timestamp startDate) {
		this.startDate = startDate;
	}

	public Timestamp getEndDate() {
		return endDate;
	}

	public void setEndDate(final Timestamp endDate) {
		this.endDate = endDate;
	}

	public String getAge() {
		return age;
	}

	public void setAge(final String age) {
		this.age = age;
	}

	public String getGender() {
		return gender;
	}

	public void setGender(final String gender) {
		this.gender = gender;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(final String language) {
		this.language = language;
	}

	public String getSkills() {
		return skills;
	}

	public void setSkills(final String skills) {
		this.skills = skills;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(final String region) {
		this.region = region;
	}

	public String getStorage() {
		return storage;
	}

	public void setStorage(final String storage) {
		this.storage = storage;
	}

	public Boolean getShare() {
		return share;
	}

	public void setShare(final Boolean share) {
		this.share = share;
	}

	public String getImage() {
		return image;
	}

	public void setImage(final String image) {
		this.image = image;
	}

	@Transient
	@Override
	public boolean writeAccess(final BigInteger user, final Repository repository) {
		return repository.one(Contact.class, user).getType() == ContactType.adminContent;
	}
}
