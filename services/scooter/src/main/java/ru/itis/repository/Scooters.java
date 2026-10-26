package ru.itis.repository;
import ru.itis.model.Scooter;
import ru.itis.util.ApiException;
import java.sql.*;
import java.util.*;

public class Scooters {
 static {try{Class.forName("org.postgresql.Driver");}catch(ClassNotFoundException e){throw new ExceptionInInitializerError(e);}}
 private Connection connect()throws SQLException{return DriverManager.getConnection(System.getenv("DB_URL"),System.getenv().getOrDefault("DB_USER",System.getenv("DB_USERNAME")),System.getenv("DB_PASSWORD"));}
 private Scooter map(ResultSet r,Double x,Double y)throws SQLException{
  double px=r.getDouble("position_x"),py=r.getDouble("position_y");
  if(x!=null && !Double.isFinite(Math.hypot(px-x,py-y)))throw new ApiException(400,"Слишком большие координаты");
  return new Scooter(r.getString("numeric_plate"),r.getInt("charge"),r.getString("status"),px,py,x==null?null:Math.hypot(px-x,py-y));
 }
 public List<Scooter> list(String status,Double x,Double y){
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
 private ApiException unavailable(SQLException e){return new ApiException(503,"Хранилище самокатов недоступно");}
}
