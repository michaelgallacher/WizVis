package com.sonos;


public class TransitionModel {
	private String event;
	private String condition;
	public TransitionModel(String e, String c)
	{
		event = e;
		condition = c;
	}

	public String getEvent() {return event;}
	public String getCond() { return condition;}
}
