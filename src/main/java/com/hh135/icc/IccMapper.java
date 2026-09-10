package com.hh135.icc;

import org.apache.ibatis.annotations.*;
import java.time.OffsetDateTime;
import java.util.*;
import static com.hh135.icc.Models.*;

@Mapper
public interface IccMapper {
    @Insert("INSERT INTO contract VALUES(#{id},#{contractNumber},#{customerName},#{amount},#{createdAt})")
    void insertContract(Contract contract);
    @Select("SELECT * FROM contract WHERE id=#{id}")
    Contract contract(UUID id);
    @Select("SELECT * FROM contract ORDER BY created_at DESC, id LIMIT #{limit} OFFSET #{offset}")
    List<Contract> contracts(@Param("limit") int limit, @Param("offset") int offset);
    @Insert("INSERT INTO interface_message VALUES(#{id},#{contractId},#{status},#{scenario},#{attemptCount},#{lastError},#{createdAt},#{updatedAt})")
    void insertMessage(Message message);
    @Select("SELECT * FROM interface_message WHERE id=#{id}")
    Message message(UUID id);
    @Select("SELECT * FROM interface_message WHERE id=#{id} FOR UPDATE")
    Message lockMessage(UUID id);
    @Select("SELECT * FROM interface_message ORDER BY created_at DESC, id LIMIT #{limit} OFFSET #{offset}")
    List<Message> messages(@Param("limit") int limit, @Param("offset") int offset);
    @Update("UPDATE interface_message SET status=#{status}, attempt_count=#{attemptCount}, last_error=#{lastError}, updated_at=#{updatedAt} WHERE id=#{id}")
    void updateMessage(Message message);
    @Insert("INSERT INTO interface_history VALUES(#{id},#{messageId},#{batchId},#{attemptNumber},#{status},#{errorCode},#{detail},#{processedAt})")
    void insertHistory(History history);
    @Select("SELECT * FROM interface_history WHERE message_id=#{id} ORDER BY attempt_number LIMIT #{limit} OFFSET #{offset}")
    List<History> history(@Param("id") UUID id, @Param("limit") int limit, @Param("offset") int offset);
    @Insert("INSERT INTO batch_history VALUES(#{id},#{jobName},#{status},#{totalCount},#{successCount},#{failureCount},#{startedAt},#{finishedAt})")
    void insertBatch(Batch batch);
    @Select("SELECT * FROM batch_history ORDER BY started_at DESC, id LIMIT #{limit} OFFSET #{offset}")
    List<Batch> batches(@Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM interface_history WHERE processed_at >= #{from} AND processed_at < #{until} AND status=#{status}")
    long count(@Param("from") OffsetDateTime from, @Param("until") OffsetDateTime until, @Param("status") Status status);
}
