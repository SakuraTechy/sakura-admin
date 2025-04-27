<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd" >
<mapper namespace="${packageName}.mapper.${classNamePrefix}Mapper">
    <select id="get${classNamePrefix}ById" resultType="${packageName}.model.entity.${classNamePrefix}DO">
        SELECT * FROM ${tableName} WHERE id = <#noparse>#{id}</#noparse>
    </select>
</mapper>