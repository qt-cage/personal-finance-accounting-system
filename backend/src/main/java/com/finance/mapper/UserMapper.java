package com.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM `user` WHERE id=#{id} AND deleted=0 FOR UPDATE")
    User lockForAccountDeletion(@Param("id") Long id);

    @Select("SELECT id FROM `user` WHERE id=#{id} AND deleted=0 AND status='ACTIVE' FOR UPDATE")
    Long lockActiveId(@Param("id") Long id);

    @Update("UPDATE user SET avatar = NULL WHERE id = #{userId} AND deleted = 0")
    int clearAvatar(@Param("userId") Long userId);

    @Update("UPDATE user SET password = #{newPassword}, token_version = token_version + 1 " +
            "WHERE id = #{userId} AND password = #{expectedPassword} AND deleted = 0")
    int changePasswordAndInvalidateTokens(@Param("userId") Long userId,
                                         @Param("expectedPassword") String expectedPassword,
                                         @Param("newPassword") String newPassword);

    @Update("UPDATE user SET password = #{newPassword} " +
            "WHERE id = #{userId} AND password = #{expectedPassword} AND deleted = 0")
    int upgradeLegacyPassword(@Param("userId") Long userId,
                              @Param("expectedPassword") String expectedPassword,
                              @Param("newPassword") String newPassword);

    // 包括已删除账号，避免清理仍由另一条历史记录引用的旧头像。
    @Select("SELECT CASE WHEN COUNT(*) = 1 AND MIN(id) = #{userId} THEN 1 ELSE 0 END " +
            "FROM user WHERE avatar = #{avatarPath}")
    boolean isExclusiveAvatarOwner(@Param("userId") Long userId, @Param("avatarPath") String avatarPath);
}
