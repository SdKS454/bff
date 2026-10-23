package ru.itis.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import ru.itis.dao.ReasonDao;
import ru.itis.dao.impl.ReasonDaoImpl;
import ru.itis.entity.Reason;
import ru.itis.entity.enums.BlockReason;

@WebListener
public class ReasonsLoader implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ReasonDao reasonDao = new ReasonDaoImpl();
        for (BlockReason reason : BlockReason.values()) {
            reasonDao.save(new Reason(
                    reason,
                    reason.getDescription(),
                    reason.getDefaultDurationSeconds().orElse(null)));
        }
    }
}
