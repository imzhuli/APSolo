package com.solo.ximple;

class GeoDetail 
{
	public String continent = "";
	public String country = "";
	public String prov = "";
	public String city = "";
	public String zipcode = "";
	public String accuracy = "";
	public String areacode = "";
	public String adcode = "";
	
	public String lat;
	public String lng;
	public String radius;
}

public class GeoInfo {
	
	public String     code = "";
	public String     ip = "";
	public String     coordsys = "";
	public GeoDetail  data = new GeoDetail();
	
}
