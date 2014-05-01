package io.tetrapod.core.utils;

public interface Callback<V>  {     
   public void call(V data) throws Exception;     
}
