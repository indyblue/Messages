import { h } from 'preact';
import { useEffect } from 'preact/hooks';
import { signal } from '@preact/signals';
import { tryFetch } from './utils';

const threadData = signal(null);

const ThreadComponent = () => {
  useEffect(() => {
    tryFetch('/api/thread')
      .then(([{ value, resp }, err]) => {
        if (resp?.ok) threadData.value = value;
        if (err) console.log('error', err);
      });
  }, []);

  return h('div', null,
    threadData?.map(x => h('div', { style: { whiteSpace: 'pre-wrap' } },
      JSON.stringify(x, null, 2),
    )),
  );
};

export default ThreadComponent;
