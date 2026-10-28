package com.finance.config;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.DatabaseMetaData;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class DatabaseStartupCheckTest {
    @Test
    void rejectsUnavailableDatabaseWithoutExposingDriverMessage() throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("sensitive driver detail", "28000", 1045));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new DatabaseStartupCheck(source).run(null));
        assertTrue(error.getMessage().contains("1045"));
        assertFalse(error.getMessage().contains("sensitive driver detail"));
    }

    @Test
    void rejectsMissingUniqueConstraintsWithoutReadingUsersAndClosesResources() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet indexes = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(contains("WHERE 1 = 0"))).thenReturn(result);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getIndexInfo(any(), any(), anyString(), eq(true), eq(false))).thenReturn(indexes);
        IllegalStateException error=assertThrows(IllegalStateException.class, () -> new DatabaseStartupCheck(source).run(null));
        assertTrue(error.getMessage().contains("missing unique constraint"));
        verify(result, times(14)).close();
        verify(indexes).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test void rejectsOldUserColumnsWithoutReturningSqlOrDriverDetails() throws Exception {
        DataSource source=mock(DataSource.class); Connection connection=mock(Connection.class); Statement statement=mock(Statement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(contains("`role`"))).thenThrow(new SQLException("sensitive SQL detail", "42S22", 1054));
        IllegalStateException error=assertThrows(IllegalStateException.class,()->new DatabaseStartupCheck(source).run(null));
        assertTrue(error.getMessage().contains("user columns")); assertFalse(error.getMessage().contains("sensitive SQL detail"));
    }
}
