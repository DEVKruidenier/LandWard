package io.github.devkruidenier.landward.storage.migration;

import io.github.devkruidenier.landward.model.*;
import io.github.devkruidenier.landward.storage.StorageSnapshot;
import java.util.*;
import java.util.function.*;

/** Bounded first-difference diagnostics; never dumps names or connection settings. */
final class SnapshotDifference {
    private SnapshotDifference() {}
    static String describe(StorageSnapshot source, StorageSnapshot target) {
        String counts="counts(source/target): lands="+source.lands().size()+"/"+target.lands().size()
                +", chunks="+source.claims().size()+"/"+target.claims().size()
                +", members="+source.members().size()+"/"+target.members().size()
                +", safeZones="+source.adminLandIds().size()+"/"+target.adminLandIds().size()
                +", features="+source.features().size()+"/"+target.features().size()
                +", warps="+warps(source)+"/"+warps(target)+"; ";
        String difference=compare("land",source.lands(),target.lands(),Land::id,(a,b)->fields(
                new Field("ownerUuid",a.ownerId(),b.ownerId()),new Field("name",a.name(),b.name()),
                new Field("createdAt",a.createdAt(),b.createdAt()),new Field("memberPermissions",a.memberPermissions(),b.memberPermissions()),
                new Field("protectionSettings",a.protectionSettings(),b.protectionSettings())));
        if(difference==null)difference=compare("chunk",source.claims(),target.claims(),ClaimedChunk::position,
                (a,b)->fields(new Field("landId",a.landId(),b.landId())));
        if(difference==null)difference=compare("member",source.members(),target.members(),m->m.landId()+":"+m.playerId(),
                (a,b)->fields(new Field("permissions",a.permissions(),b.permissions())));
        if(difference==null)difference=compare("safeZone",source.adminLandIds(),target.adminLandIds(),Function.identity(),(a,b)->null);
        if(difference==null)difference=compare("features",source.features(),target.features(),LandFeatures::landId,(a,b)->{
            String environment=fields(new Field("environment",a.environment().allowed(),b.environment().allowed()));
            return environment!=null?environment:compare("warp",a.warps(),b.warps(),LandWarp::id,(x,y)->fields(
                    new Field("name",x.name(),y.name()),new Field("worldUuid",x.worldId(),y.worldId()),
                    new Field("x",x.x(),y.x()),new Field("y",x.y(),y.y()),new Field("z",x.z(),y.z()),
                    new Field("yaw",x.yaw(),y.yaw()),new Field("pitch",x.pitch(),y.pitch())));
        });
        if(difference==null)difference=compare("protection",source.protections(),target.protections(),p->p.id(),(a,b)->fields(new Field("record",a,b)));
        if(difference==null)difference=compare("placement",source.placements(),target.placements(),p->p.position(),(a,b)->fields(new Field("record",a,b)));
        return counts+(difference==null?"no differing field found":difference);
    }
    private static long warps(StorageSnapshot snapshot){return snapshot.features().stream().mapToLong(f->f.warps().size()).sum();}
    private static <T,K> String compare(String category,List<T> source,List<T> target,Function<T,K> key,BiFunction<T,T,String> detail){
        Map<K,T> a=new HashMap<>(),b=new HashMap<>();source.forEach(v->a.put(key.apply(v),v));target.forEach(v->b.put(key.apply(v),v));
        var keys=new HashSet<K>(a.keySet());keys.addAll(b.keySet());
        for(K id:keys.stream().sorted(Comparator.comparing(Object::toString)).toList()){
            if(!a.containsKey(id))return category+"="+id+" unexpected in target";
            if(!b.containsKey(id))return category+"="+id+" missing from target";
            if(!Objects.equals(a.get(id),b.get(id))){String message=detail.apply(a.get(id),b.get(id));if(message!=null)return category+"="+id+" "+message;}
        }
        return null;
    }
    private record Field(String name,Object source,Object target) {}
    private static String fields(Field... fields){for(var f:fields)if(!Objects.equals(f.source,f.target))return "field="+f.name+" source="+value(f.source)+" target="+value(f.target);return null;}
    private static String value(Object value){return value instanceof String text?"<text length="+text.length()+">":String.valueOf(value);}
}
