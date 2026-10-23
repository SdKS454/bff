package ru.itis.dao;

import ru.itis.entity.Reason;

import java.util.List;

public interface ReasonDao {

    void save(Reason reason);

    List<Reason> findAll();
}
