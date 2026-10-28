package ru.itis.repository;
import ru.itis.model.Scooter;
import ru.itis.util.ApiException;
import java.sql.*;
import java.util.*;

public class Scooters {
 static {try{Class.forName("org.postgresql.Driver");}catch(ClassNotFoundException e){throw new ExceptionInInitializerError(e);}}
 private Connection connect()throws SQLException{return DriverManager.getConnection(System.getenv("DB_URL"),System.getenv().getOrDefault("DB_USER",System.getenv("DB_USERNAME")),System.getenv("DB_PASSWORD"));}
 private static boolean validGps(double longitude,double latitude) {
  return Double.isFinite(longitude)&&Double.isFinite(latitude)&&Math.abs(longitude)<=180&&Math.abs(latitude)<=90;
 }
 private static double distanceMeters(double lon1,double lat1,double lon2,double lat2) {
  double latDelta=Math.toRadians(lat2-lat1),lonDelta=Math.toRadians(lon2-lon1);
  double a=Math.pow(Math.sin(latDelta/2),2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.pow(Math.sin(lonDelta/2),2);
  a=Math.max(0,Math.min(1,a));
  return 6371008.8*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
 }
 private Scooter map(ResultSet r,Double x,Double y)throws SQLException{
  double px=r.getDouble("position_x"),py=r.getDouble("position_y");
  Double distance=null;
  if(x!=null) {
   if(!validGps(px,py))throw new ApiException(503,"В базе есть самокат с некорректными GPS-координатами");
   distance=distanceMeters(x,y,px,py);
  }
  return new Scooter(r.getString("numeric_plate"),r.getInt("charge"),r.getString("status"),px,py,distance);
 }
 public List<Scooter> list(String status,Double x,Double y){
  if(x!=null&&(y==null||!validGps(x,y)))throw new ApiException(400,"Нужны GPS-координаты: posX - долгота от -180 до 180, posY - широта от -90 до 90");
  String sql="SELECT * FROM scooter WHERE NOT is_deleted"+(status==null?"":" AND status=?")+" ORDER BY numeric_plate";
  try(var c=connect();var q=c.prepareStatement(sql)){
   if(status!=null)q.setString(1,status);List<Scooter> result=new ArrayList<>();
   try(var r=q.executeQuery()){while(r.next())result.add(map(r,x,y));}
   if(x!=null)result.sort(Comparator.comparingDouble(Scooter::distance).thenComparing(Scooter::numberPlate));return result;
  }catch(SQLException e){throw unavailable(e);}
 }
 public Scooter get(String plate){try(var c=connect();var q=c.prepareStatement("SELECT * FROM scooter WHERE numeric_plate=? AND NOT is_deleted")){q.setString(1,plate);try(var r=q.executeQuery()){if(!r.next())throw new ApiException(404,"Самокат не найден");return map(r,null,null);}}catch(SQLException e){throw unavailable(e);}}
 public Scooter reserve(String plate,UUID ride,UUID user){
  try(var c=connect()){
   c.setAutoCommit(false);
   try {
    try(var q=c.prepareStatement("INSERT INTO scooter_reservations(ride_id,number_plate,user_id,released) VALUES(?,?,?,false) ON CONFLICT DO NOTHING")){
     q.setObject(1,ride);q.setString(2,plate);q.setObject(3,user);q.executeUpdate();
    }
    try(var q=c.prepareStatement("SELECT * FROM scooter_reservations WHERE ride_id=? FOR UPDATE")){
     q.setObject(1,ride);try(var r=q.executeQuery()){
      r.next();if(r.getBoolean("released")||!plate.equals(r.getString("number_plate"))||!user.equals(r.getObject("user_id",UUID.class)))throw new ApiException(409,"Резервация отменена или изменена");
     }
    }
    try(var q=c.prepareStatement("UPDATE scooter SET status='IN_RIDE',reservation_id=?,reserved_by=? WHERE numeric_plate=? AND status='READY' AND charge>0 AND NOT is_deleted AND reservation_id IS DISTINCT FROM ?")){
     q.setObject(1,ride);q.setObject(2,user);q.setString(3,plate);q.setObject(4,ride);
     if(q.executeUpdate()==0){try(var check=c.prepareStatement("SELECT * FROM scooter WHERE numeric_plate=? AND NOT is_deleted")){
      check.setString(1,plate);try(var r=check.executeQuery()){
       if(!r.next())throw new ApiException(404,"Самокат не найден");
       if(!"IN_RIDE".equals(r.getString("status"))||!ride.equals(r.getObject("reservation_id",UUID.class))||!user.equals(r.getObject("reserved_by",UUID.class)))throw new ApiException(409,"Самокат недоступен");
      }
     }}
    }
    c.commit();return get(plate);
   }catch(SQLException|RuntimeException e){c.rollback();throw e;}
  }catch(SQLException e){throw unavailable(e);}
 }
 public Scooter release(String plate,UUID ride,Double x,Double y){
  try(var c=connect()){
   c.setAutoCommit(false);
   try {
    try(var q=c.prepareStatement("INSERT INTO scooter_reservations(ride_id,number_plate,released) VALUES(?,?,true) ON CONFLICT DO NOTHING")){
     q.setObject(1,ride);q.setString(2,plate);q.executeUpdate();
    }
    boolean released;
    try(var q=c.prepareStatement("SELECT * FROM scooter_reservations WHERE ride_id=? FOR UPDATE")){
     q.setObject(1,ride);try(var r=q.executeQuery()){
      r.next();if(!plate.equals(r.getString("number_plate")))throw new ApiException(409,"Другая резервация самоката");
      released=r.getBoolean("released");
     }
    }
    if(!released){
     try(var q=c.prepareStatement("UPDATE scooter SET status='READY',position_x=COALESCE(?,position_x),position_y=COALESCE(?,position_y) WHERE numeric_plate=? AND NOT is_deleted AND status='IN_RIDE' AND reservation_id=?")){
      q.setObject(1,x);q.setObject(2,y);q.setString(3,plate);q.setObject(4,ride);
      if(q.executeUpdate()==0)throw new ApiException(409,"Другая резервация самоката");
     }
     try(var q=c.prepareStatement("UPDATE scooter_reservations SET released=true WHERE ride_id=?")){q.setObject(1,ride);q.executeUpdate();}
    }
    c.commit();return get(plate);
   }catch(SQLException|RuntimeException e){c.rollback();throw e;}
  }catch(SQLException e){throw unavailable(e);}
 }
 public Scooter position(String plate,UUID ride,double x,double y) {
  try(var c=connect();var q=c.prepareStatement("UPDATE scooter SET position_x=?,position_y=? WHERE numeric_plate=? AND NOT is_deleted AND status='IN_RIDE' AND reservation_id=?")) {
   q.setDouble(1,x);q.setDouble(2,y);q.setString(3,plate);q.setObject(4,ride);
   if(q.executeUpdate()!=1)throw new ApiException(409,"Самокат больше не принадлежит активной поездке");return get(plate);
  }catch(SQLException e){throw unavailable(e);}
 }
 public Scooter create(String plate,double x,double y) {
  if(plate==null||plate.equals(".")||plate.equals("..")||!plate.matches("[A-Za-z0-9_.-]{1,64}"))throw new ApiException(400,"Номер: 1–64 символа латиницы, цифр, _, . или -");
  if(!validGps(x,y))throw new ApiException(400,"posX: долгота от -180 до 180; posY: широта от -90 до 90");
  try(var c=connect();var q=c.prepareStatement("INSERT INTO scooter(numeric_plate,charge,status,position_x,position_y) VALUES(?,100,'READY',?,?) RETURNING *")) {
   q.setString(1,plate);q.setDouble(2,x);q.setDouble(3,y);try(var rows=q.executeQuery()){rows.next();return map(rows,null,null);}
  }catch(SQLException e){if("23505".equals(e.getSQLState()))throw new ApiException(409,"Этот номер уже использован, в том числе удалённым самокатом");throw unavailable(e);}
 }
 public Scooter charge(String plate) {
  try(var c=connect()) {
   c.setAutoCommit(false);
   try {
    lockAvailable(c,plate);
    try(var q=c.prepareStatement("UPDATE scooter SET charge=100,status=CASE WHEN status='CHARGING' THEN 'READY' ELSE status END WHERE numeric_plate=? RETURNING *")) {
     q.setString(1,plate);try(var rows=q.executeQuery()){rows.next();var scooter=map(rows,null,null);c.commit();return scooter;}
    }
   }catch(SQLException|RuntimeException e){c.rollback();throw e;}
  }catch(SQLException e){throw unavailable(e);}
 }
 public void delete(String plate) {
  try(var c=connect()) {
   c.setAutoCommit(false);
   try {
    // Lock the same row used by reserve: deleting/charging cannot interrupt an active ride.
    try(var q=c.prepareStatement("SELECT status,is_deleted FROM scooter WHERE numeric_plate=? FOR UPDATE")) {
     q.setString(1,plate);try(var rows=q.executeQuery()) {
      if(!rows.next())throw new ApiException(404,"Самокат не найден");
      if(!rows.getBoolean("is_deleted")&&"IN_RIDE".equals(rows.getString("status")))throw new ApiException(409,"Нельзя удалить самокат во время поездки");
     }
    }
    try(var q=c.prepareStatement("UPDATE scooter SET is_deleted=true WHERE numeric_plate=?")){q.setString(1,plate);q.executeUpdate();}
    c.commit();
   }catch(SQLException|RuntimeException e){c.rollback();throw e;}
  }catch(SQLException e){throw unavailable(e);}
 }
 private void lockAvailable(Connection c,String plate)throws SQLException {
  try(var q=c.prepareStatement("SELECT status FROM scooter WHERE numeric_plate=? AND NOT is_deleted FOR UPDATE")) {
   q.setString(1,plate);try(var rows=q.executeQuery()){
    if(!rows.next())throw new ApiException(404,"Самокат не найден");
    if("IN_RIDE".equals(rows.getString("status")))throw new ApiException(409,"Нельзя зарядить самокат во время поездки");
   }
  }
 }
 private ApiException unavailable(SQLException e){return new ApiException(503,"Хранилище самокатов недоступно");}
}
